#!/usr/bin/env node

import { execFile } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const debugPackage = 'dev.vendicated.vencord.dbg';
const commandTimeout = 5_000;
const discoveryTimeout = 30_000;

const exec = promisify(execFile);

async function adb(serial, ...args) {
    const { stdout } = await exec('adb', ['-s', serial, ...args], {
        timeout: commandTimeout
    });

    return stdout.trim();
}

async function findAppProcesses(serial, packageName) {
    try {
        const output = await adb(serial, 'shell', 'pidof', packageName);
        return output.split(/\s+/).filter(Boolean);
    } catch (error) {
        // pidof exits with status 1 when the app is not running.
        if (error.code === 1 && !error.stderr) {
            return [];
        }

        throw error;
    }
}

function openWebSocket(url) {
    const socket = new WebSocket(url);

    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            socket.close();
            reject(new Error('Timed out connecting to the WebView debugger.'));
        }, commandTimeout);

        socket.onopen = () => {
            clearTimeout(timer);
            socket.onerror = null;
            resolve(socket);
        };

        socket.onerror = () => {
            clearTimeout(timer);
            reject(new Error('Could not connect to the WebView debugger.'));
        };
    });
}

function createSession(socket, removeForward) {
    const pending = new Map();
    let nextId = 0;

    socket.onmessage = ({ data }) => {
        const { id, result, error } = JSON.parse(data);
        const request = pending.get(id);

        if (!request) {
            return;
        }

        clearTimeout(request.timer);
        pending.delete(id);

        if (error) {
            request.reject(new Error(error.message));
        } else {
            request.resolve(result);
        }
    };

    socket.onclose = () => {
        for (const { timer, reject } of pending.values()) {
            clearTimeout(timer);
            reject(new Error('The WebView debugger disconnected.'));
        }

        pending.clear();
    };

    async function send(method, params = {}) {
        if (socket.readyState !== WebSocket.OPEN) {
            throw new Error('The WebView debugger disconnected.');
        }

        const id = ++nextId;
        const message = JSON.stringify({ id, method, params });
        socket.send(message);

        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                pending.delete(id);
                reject(new Error(`CDP timed out running ${method}.`));
            }, commandTimeout);

            pending.set(id, { resolve, reject, timer });
        });
    }

    async function evaluate(expression) {
        const { result, exceptionDetails } = await send('Runtime.evaluate', {
            expression,
            awaitPromise: true,
            returnByValue: true
        });

        if (exceptionDetails) {
            const { exception, text } = exceptionDetails;
            throw new Error(exception?.description ?? text);
        }

        return result.unserializableValue ?? result.value;
    }

    async function close() {
        socket.close();
        await removeForward();
    }

    return { send, evaluate, close };
}

async function connectToWebView(serial, pid, matches) {
    const port = await adb(serial, 'forward', 'tcp:0', `localabstract:webview_devtools_remote_${pid}`);
    const removeForward = () => adb(serial, 'forward', '--remove', `tcp:${port}`);
    let socket;

    try {
        const response = await fetch(`http://127.0.0.1:${port}/json/list`, {
            signal: AbortSignal.timeout(commandTimeout)
        }).catch(() => null);

        if (!response || !response.ok) {
            return null;
        }

        const targets = await response.json();
        const page = targets.find(target =>
            target.type === 'page' && target.webSocketDebuggerUrl && matches(target)
        );

        if (!page) {
            return null;
        }

        const { pathname, search } = new URL(page.webSocketDebuggerUrl);
        socket = await openWebSocket(`ws://127.0.0.1:${port}${pathname}${search}`);

        return createSession(socket, removeForward);
    } finally {
        if (!socket) {
            await removeForward();
        }
    }
}

export async function connect({ packageName = debugPackage, matches = () => true } = {}) {
    if (!/^[\w.]+$/.test(packageName)) {
        throw new Error('Invalid Android package name.');
    }

    const launcher = fileURLToPath(new URL('./launchEmulator', import.meta.url));
    const { stdout } = await exec(launcher, ['--running'], { timeout: commandTimeout });
    const serial = stdout.trim();
    const bootStatus = await adb(serial, 'shell', 'getprop', 'sys.boot_completed');

    if (bootStatus !== '1') {
        throw new Error(`Wait for ${serial} to finish booting with scripts/launchEmulator.`);
    }

    const deadline = Date.now() + discoveryTimeout;

    do {
        const pids = await findAppProcesses(serial, packageName);

        for (const pid of pids) {
            const session = await connectToWebView(serial, pid, matches);

            if (session) {
                return session;
            }
        }

        await delay(500);
    } while (Date.now() < deadline);

    throw new Error(`No debuggable WebView for ${packageName} on ${serial}. Open the debug app and try again.`);
}

async function main() {
    const usage = [
        'Usage:',
        '  scripts/cdp.mjs eval <javascript|->',
        '  scripts/cdp.mjs send <CDP.method> [params-json]'
    ].join('\n');

    const [command, argument, json, ...extra] = process.argv.slice(2);

    if (command === '--help') {
        console.log(usage);
        return;
    }

    const isEval = command === 'eval';
    const isSend = command === 'send';

    if ((!isEval && !isSend) || !argument || extra.length || (isEval && json !== undefined)) {
        throw new Error(usage);
    }

    const input = isEval && argument === '-' ? readFileSync(0, 'utf8') : argument;
    const params = json === undefined ? undefined : JSON.parse(json);
    const cdp = await connect({ packageName: process.env.WEBVIEW_PACKAGE });

    try {
        const result = isEval ? await cdp.evaluate(input) : await cdp.send(input, params);

        console.log(JSON.stringify(result, null, 2) ?? 'undefined');
    } finally {
        await cdp.close();
    }
}

if (import.meta.main) {
    main().catch(error => {
        console.error(error.message);
        process.exitCode = 1;
    });
}
