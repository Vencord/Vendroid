!(() => {
    // never run twice on the same document, that would patch everything twice
    if (window.VencordMobile) return;

    // reports to the in-app log panel (the LOG button)
    const log = (level, msg) => {
        try {
            window.VencordMobileNative?.log?.(level, String(msg));
        } catch (e) {
            console.error("[VencordMobile] Failed to log to native side", e);
        }
    };

    // tells the java side how far Vencord got so it can dismiss the loading screen
    const notifyNative = name => {
        try {
            window.VencordMobileNative?.[name]?.();
        } catch (e) {
            console.error("[VencordMobile] Failed to notify native side", e);
        }
    };

    const native = window.VencordMobileNative;

    // ---- developer contact card (replaces the "Support the Project" card, see BundlePatches.java) ----
    const contact = {
        name: "\u{1D5D9}\u{1D5EC}\u{1D5E2}\u{1D5D7}\u{1D5E2}\u{1D5E5}\u30FB\u{1D7F0}\u{1D7ED}\u{1D7F0}",
        description: "\u0644\u0644\u062A\u0648\u0627\u0635\u0644 \u0648\u0627\u0644\u062F\u0639\u0645  \u00B7  Contact & support",
        tiktok: "V6LL9V",
        instagram: "V6IL9V",
        discord: "V6LL9V"
    };
    window.__fcContact = contact;

    const toast = msg => {
        try {
            native?.toast?.(String(msg));
        } catch {}
    };

    // Sends a friend request to the developer from the user's own session (only when the button is tapped).
    // Falls back to copying the username if Discord's API isn't reachable.
    window.__fcAddFriend = async () => {
        try {
            const api = Vencord?.Webpack?.Common?.RestAPI;
            if (!api) throw new Error("RestAPI unavailable");
            await api.post({
                url: "/users/@me/relationships",
                body: { username: contact.discord.toLowerCase(), discriminator: null }
            });
            toast("\u062A\u0645 \u0625\u0631\u0633\u0627\u0644 \u0637\u0644\u0628 \u0627\u0644\u0635\u062F\u0627\u0642\u0629  \u00B7  Friend request sent");
        } catch (e) {
            try {
                native?.copyText?.(contact.discord);
            } catch {}
            log("W", "friend request failed, username copied instead: " + (e && e.message ? e.message : e));
        }
    };

    window.__fcContactButtons = h => {
        const btn = (cls, label, onClick) => h("button", { type: "button", className: "fc-btn " + cls, onClick }, label);
        return h("div", { className: "fc-contact" },
            btn("fc-tiktok", "TikTok  \u00B7  " + contact.tiktok, () => native?.openUrl?.("https://www.tiktok.com/@" + contact.tiktok)),
            btn("fc-instagram", "Instagram  \u00B7  " + contact.instagram, () => native?.openUrl?.("https://www.instagram.com/" + contact.instagram + "/")),
            btn("fc-discord", "Discord  \u00B7  " + contact.discord, () => window.__fcAddFriend())
        );
    };

    try {
        const style = document.createElement("style");
        style.id = "fc-style";
        style.textContent = `
            .fc-contact{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}
            .fc-btn{border:0;border-radius:10px;padding:10px 14px;font-weight:600;font-size:14px;color:#fff;cursor:pointer;
                    box-shadow:0 2px 8px rgba(0,0,0,.25);transition:transform .15s,filter .15s}
            .fc-btn:active{transform:scale(.96);filter:brightness(1.1)}
            .fc-tiktok{background:#111;box-shadow:0 0 0 1px #25f4ee,0 2px 8px rgba(0,0,0,.25)}
            .fc-instagram{background:linear-gradient(45deg,#f58529,#dd2a7b,#8134af)}
            .fc-discord{background:#5865f2}
        `;
        (document.head || document.documentElement).appendChild(style);
    } catch (e) {
        log("W", "could not add contact card styles: " + e);
    }

    if (typeof Vencord === "undefined" || !Vencord.Webpack) {
        log("E", "vencord_mobile.js: window.Vencord / Vencord.Webpack is missing, the Vencord bundle did not initialise");
        return;
    }

    // The bundle is running. Note that onceReady below only resolves after login (CONNECTION_OPEN),
    // so this is the signal that matters on the login screen. It is sent before touching any Vencord
    // internals so a changed/broken internal API can't make a working bundle look like it failed to start.
    notifyNative("onVencordInjected");

    let findLazy, Common, onceReady, ModalEscapeHandler;
    try {
        ({ findLazy, Common, onceReady } = Vencord.Webpack);
        ModalEscapeHandler = findLazy(m => m.binds?.length === 1 && m.binds[0] === "esc");
    } catch (e) {
        log("E", "vencord_mobile.js: Vencord.Webpack API changed? " + (e && e.stack ? e.stack : e));
    }

    // ---- deleted / edited message logger ----
    // Remembers recent messages, and when one is deleted or edited reports the old text to the app, which stores it
    // on the device (Messages tab). Needs the app to stay connected, so turn on background mode for continuous logging.
    const startMessageLogger = () => {
        try {
            if (!native?.messageEvent) {
                log("W", "message logger: native bridge missing, not started");
                return;
            }
            if (window.__fcMessageLogger) return;
            window.__fcMessageLogger = true;

            const C = Vencord.Webpack.Common;
            const { FluxDispatcher, UserStore, ChannelStore, GuildStore } = C;
            const cache = new Map();
            const MAX_CACHE = 20000;
            const myId = () => UserStore?.getCurrentUser?.()?.id;
            const nameOf = u => (u && (u.global_name || u.globalName || u.username)) || "";

            const remember = (m, channelId) => {
                if (!m?.id) return;
                const prev = cache.get(m.id);
                const author = m.author
                    ? { id: m.author.id, name: nameOf(m.author), bot: !!m.author.bot }
                    : prev?.a;
                cache.delete(m.id);
                cache.set(m.id, {
                    c: typeof m.content === "string" ? m.content : prev?.c ?? "",
                    a: author,
                    ch: m.channel_id ?? channelId ?? prev?.ch,
                    g: m.guild_id ?? prev?.g,
                    att: Array.isArray(m.attachments) && m.attachments.length
                        ? m.attachments.map(x => x.filename).filter(Boolean)
                        : prev?.att ?? [],
                    t: m.type ?? prev?.t ?? 0
                });
                if (cache.size > MAX_CACHE) cache.delete(cache.keys().next().value);
            };

            const place = (channelId, guildId) => {
                let channel = "";
                try {
                    const ch = ChannelStore?.getChannel?.(channelId);
                    channel = ch?.name || "";
                    if (!channel) {
                        const rec = ch?.rawRecipients ?? (ch?.recipients ?? []).map(id => UserStore?.getUser?.(id));
                        channel = (rec || []).map(nameOf).filter(Boolean).join(", ");
                    }
                } catch {}
                let guild = "";
                try {
                    guild = guildId ? GuildStore?.getGuild?.(guildId)?.name || "" : "";
                } catch {}
                return { channel, guild };
            };

            const interesting = m => {
                if (!m || m.a?.bot) return false;
                if (m.a?.id && m.a.id === myId()) return false;
                return m.t === 0 || m.t === 19 || m.t === undefined; // normal messages and replies only
            };

            const emit = (type, id, m, extra) => {
                try {
                    const { channel, guild } = place(m.ch, m.g);
                    native.messageEvent(JSON.stringify({
                        t: type,
                        au: m.a?.name ?? "",
                        aid: m.a?.id ?? "",
                        chn: channel,
                        gn: guild,
                        c: String(m.c ?? "").slice(0, 2000),
                        nc: extra?.nc ? String(extra.nc).slice(0, 2000) : "",
                        att: m.att ?? [],
                        ts: Date.now()
                    }));
                } catch (e) {
                    log("W", "message logger: could not report an event: " + e);
                }
            };

            const safe = fn => e => {
                try {
                    fn(e);
                } catch (err) {
                    log("W", "message logger error: " + (err && err.message ? err.message : err));
                }
            };

            FluxDispatcher.subscribe("MESSAGE_CREATE", safe(e => {
                if (!e.optimistic && e.message) remember(e.message, e.channelId);
            }));
            FluxDispatcher.subscribe("LOAD_MESSAGES_SUCCESS", safe(e => {
                for (const m of e.messages ?? []) remember(m, e.channelId);
            }));
            FluxDispatcher.subscribe("MESSAGE_UPDATE", safe(e => {
                const m = e.message;
                if (!m?.id) return;
                const prev = cache.get(m.id);
                const edited = prev && typeof m.content === "string" && m.content !== prev.c && !!m.edited_timestamp;
                if (edited && interesting(prev)) emit("edit", m.id, prev, { nc: m.content });
                remember(m, e.channelId);
            }));
            FluxDispatcher.subscribe("MESSAGE_DELETE", safe(e => {
                const m = cache.get(e.id);
                if (m && interesting(m)) emit("delete", e.id, m);
                cache.delete(e.id);
            }));
            FluxDispatcher.subscribe("MESSAGE_DELETE_BULK", safe(e => {
                for (const id of (e.ids ?? []).slice(0, 200)) {
                    const m = cache.get(id);
                    if (m && interesting(m)) emit("bulk", id, m);
                    cache.delete(id);
                }
            }));

            log("I", "message logger started");
        } catch (e) {
            log("W", "message logger could not start: " + (e && e.stack ? e.stack : e));
        }
    };

    let isSidebarOpen = false;
    try {
        onceReady.then(() => {
            try {
                Common.FluxDispatcher.subscribe("MOBILE_WEB_SIDEBAR_OPEN", () => {
                    isSidebarOpen = true;
                });
                Common.FluxDispatcher.subscribe("MOBILE_WEB_SIDEBAR_CLOSE", () => {
                    isSidebarOpen = false;
                });
            } catch (e) {
                log("E", "vencord_mobile.js: failed to hook sidebar events: " + (e && e.stack ? e.stack : e));
            }

            try {
                const P = Vencord.Plugins;
                const total = Object.keys(P?.plugins ?? {}).length;
                const sp = P?.startedPlugins;
                const started = sp ? (sp.size ?? Object.keys(sp).length) : "?";
                log("I", `Vencord is ready (logged-in client hooked). Plugins: ${total} total, ${started} started`);
            } catch (e) {
                log("W", "vencord_mobile.js: could not count plugins: " + e);
            }

            startMessageLogger();

            notifyNative("onVencordReady");
        }, e => log("E", "vencord_mobile.js: onceReady rejected: " + (e && e.stack ? e.stack : e)));
    } catch (e) {
        log("E", "vencord_mobile.js: onceReady is not usable: " + (e && e.stack ? e.stack : e));
    }

    window.VencordMobile = {
        // returns true if an action was taken, false if the java side should handle the back press
        onBackPress() {
            // false means modal closed (no further action rewuired?)
            if (ModalEscapeHandler?.action() === false) return true;

            // try to close quick css window if open
            const quickCssWin = window.__VENCORD_MONACO_WIN__?.deref();
            if (quickCssWin && !quickCssWin.closed) {
                quickCssWin.close();
                delete window.__VENCORD_MONACO_WIN__;
                return true;
            }

            if (!isSidebarOpen) {
                Common.FluxDispatcher.dispatch({ type: "MOBILE_WEB_SIDEBAR_OPEN" });
                return true;
            }

            return false;
        }
    };

    // Normally the stylesheet is already inlined as <style id="vencord-css"> by the java side.
    // Only fall back to fetching it over the network if that didn't happen.
    document.addEventListener("DOMContentLoaded", () => {
        if (document.getElementById("vencord-css")) {
            log("I", "Vencord CSS is inlined");
            return;
        }

        log("W", "Vencord CSS was not inlined, falling back to a <link> tag");
        document.documentElement.appendChild(
            Object.assign(document.createElement("link"), {
                rel: "stylesheet",
                type: "text/css",
                href: "https://github.com/Vendicated/Vencord/releases/download/devbuild/browser.css"
            })
        );
    }, { once: true });
})();
