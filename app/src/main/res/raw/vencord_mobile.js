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

    // ---- About Us (a page in the FyoCord settings, next to Plugins / Themes / ...) ----
    const contact = {
        name: "FYODOR",
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

    // Discord has no "open profile" link for a username, so tapping the button copies it
    window.__fcCopyDiscord = () => {
        try {
            native?.copyText?.(contact.discord);
        } catch (e) {
            log("W", "could not copy the Discord username: " + e);
        }
    };

    const APP_ICONS = {
        tiktok: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAYAAADimHc4AAABEUlEQVR42u3cPRIBMRiA4XxGp3IIOtdxSI5D5RA01FFrNkVks5t53tYYP48vIyM20so7vJ+55v6P3b7p84uImLp9k9Q1AAAACAAAAQAgAAAEAIAAABAAAAIAQAAACAAAARizrbegbTnnbAIsQQIAQAAACIB9wGDf80/nydvjdjEBliABACAAAATAPmC0T/D9GoU/CpsAS5AAABAAAAIw3D6gdO4lNb7ejgkQAAACAEAAAOj/ALlQmuF8fc/HNwGWIAEAIAAABCD5PWBxla7/f/y8TIAAABAAAAIAQL9F73M/zV9g4VxR7eurPbdkAixBAAQAgAAA0BL3AUvfJ6z9/wMmAAAAAQAgAAAkSZIkSTP0BSSRX/hVRsLEAAAAAElFTkSuQmCC",
        instagram: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAYAAADimHc4AAABMklEQVR42u3dvY0CMRCAUQ8iggbIiaAcUjoB0i1lU8pZInIaIDYd4GBleX/eF3O6E08zshEHkUb2Pt9zWnHHoYsxP79JahoAAAAEAIAArLOofc4/XJ+zfoI+/aXqPcEEWEEABACAAABQi3tA7XP+/v6KOT9B3+6Ua94TTIAVBEAAAAgAAE3xHlD7nJ9zbvq+ooiIlvcEE2AFARAAAAIAQA3a1v4FpXP+2HP43P8+E2AFARAAAAIAQEu8Byy93W34/4D+YQKsIAEAIAAABADAwopCAEyAAAAQAAACAEAAAAgAAAEAIABpBe8LKr0mvvz/EzYBVpAAABAAAAKQfG5o8rmhJsAKEgAAAgBAAHyPWPI9YrKCAAgAAAEAoIndA2rfE+Ze6ZxvAqwgAQAgAAAEYIL9AH3XShF97bDGAAAAAElFTkSuQmCC",
        discord: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAYAAADimHc4AAAA5klEQVR42u3dyw2EIBRA0Ydxa4E2YFE2YIEWgCW4IPhUzl3Px+EMZEyGECFJkiRptMrdA2qttekNSilfHqDen3/yHcwNAAAAAgBAAAa9D2j9nSszAIAAABAAAAIAQAAACAAAAQAgAK9vbn2BdTubnn/sS+oAZF+/GWAJAiAAAAQAgAAAEAAAAgBAAAAo7A8wAwQAgAAAEAAAAgBAAAAIAAABAKDI3x8Qnf9/n13v/QtmgCUIgAAAEAAAih+eHzD8ADs/wBIkAAAEAIAAfPA+4O/njGVfvxlgCQIgAAAEAIAkSZIk6YEujLMlmOHdKoUAAAAASUVORK5CYII="
    };

    const ICON = "data:image/png;base64,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";

    // 5x7 pixel font for the letters we need
    const GLYPHS = {
        F: ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
        Y: ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
        O: ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
        D: ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
        R: ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
        C: ["01110", "10001", "10000", "10000", "10000", "10001", "01110"]
    };

    const pixelText = (h, word, cell) => {
        const rects = [];
        let i = 0;
        for (const ch of word) {
            const g = GLYPHS[ch];
            if (g) {
                g.forEach((row, y) => [...row].forEach((v, x) => {
                    if (v !== "1") return;
                    const px = (i * 6 + x) * cell, py = y * cell;
                    rects.push(h("rect", { key: "s" + i + "-" + x + "-" + y, x: px + cell / 2, y: py + cell / 2, width: cell, height: cell, fill: "#555" }));
                    rects.push(h("rect", { key: "w" + i + "-" + x + "-" + y, x: px, y: py, width: cell, height: cell, fill: "#fff" }));
                }));
            }
            i++;
        }
        const w = (word.length * 6 - 1) * cell + cell, hgt = 7 * cell + cell;
        return h("svg", { className: "fc-title", width: w, height: hgt, viewBox: "0 0 " + w + " " + hgt, shapeRendering: "crispEdges", "aria-label": word }, rects);
    };

    // menu icon: the pixel avatar itself
    const AboutIcon = h => props => h("img", { src: ICON, alt: "", width: props?.width ?? 24, height: props?.height ?? 24,
        style: { imageRendering: "pixelated", border: "2px solid currentColor", boxSizing: "border-box", background: "#000" } });

    window.__fcAbout = h => {
        const btn = (cls, app, label, user, onClick) => h("button", { type: "button", className: "fc-btn " + cls, onClick },
            h("img", { className: "fc-app", src: APP_ICONS[app], alt: "" }), h("span", { className: "fc-btn-label" }, label), h("span", { className: "fc-btn-user" }, user));
        return h("div", { className: "fc-wrap" },
            h("div", { className: "fc-card" },
                h("div", { className: "fc-scan" }),
                h("div", { className: "fc-head" },
                    h("img", { className: "fc-icon", src: ICON, alt: "FYODOR" }),
                    h("div", { className: "fc-body" },
                        pixelText(h, "FYODOR", 5),
                        h("div", { className: "fc-sub" }, "> ", contact.description, h("span", { className: "fc-cursor" }, "_")))),
                h("div", { className: "fc-rule" }),
                h("div", { className: "fc-contact" },
                    btn("fc-tiktok", "tiktok", "TikTok", contact.tiktok, () => native?.openUrl?.("https://www.tiktok.com/@" + contact.tiktok)),
                    btn("fc-instagram", "instagram", "Instagram", contact.instagram, () => native?.openUrl?.("https://www.instagram.com/" + contact.instagram + "/")),
                    btn("fc-discord", "discord", "Discord", contact.discord, () => window.__fcCopyDiscord())),
                h("div", { className: "fc-foot" }, "FyoCord  \u00B7  made with pixels")));
    };

    try {
        const style = document.createElement("style");
        style.id = "fc-style";
        style.textContent = `
            .fc-wrap{padding:8px 4px 24px;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,"DejaVu Sans Mono",monospace}
            .fc-card{position:relative;overflow:hidden;padding:22px;color:#fff;background:#000;
                     background-image:radial-gradient(rgba(255,255,255,.10) 1px,transparent 1.5px);background-size:12px 12px;
                     box-shadow:0 0 0 3px #fff,0 0 0 6px #000,0 0 0 8px #555;margin:10px}
            .fc-scan{position:absolute;inset:0;pointer-events:none;background:repeating-linear-gradient(0deg,rgba(255,255,255,.04) 0 2px,transparent 2px 4px)}
            .fc-head{position:relative;display:flex;align-items:center;gap:20px;flex-wrap:wrap}
            .fc-icon{width:104px;height:104px;flex:none;image-rendering:pixelated;border:4px solid #fff;background:#000;
                     box-shadow:6px 6px 0 #444;animation:fc-bob 2.4s steps(4) infinite}
            @keyframes fc-bob{0%,100%{transform:translateY(0)}50%{transform:translateY(-4px)}}
            .fc-body{flex:1;min-width:190px}
            .fc-title{display:block;max-width:100%;height:auto}
            .fc-sub{margin-top:12px;font-size:13px;opacity:.85;letter-spacing:.3px}
            .fc-cursor{animation:fc-blink 1s steps(2) infinite}
            @keyframes fc-blink{50%{opacity:0}}
            .fc-rule{position:relative;margin:20px 0;height:4px;background:repeating-linear-gradient(90deg,#fff 0 8px,transparent 8px 16px)}
            .fc-contact{position:relative;display:flex;flex-direction:column;gap:12px}
            .fc-btn{display:flex;align-items:center;gap:12px;width:100%;padding:12px 14px;border:3px solid #fff;border-radius:0;background:#000;color:#fff;
                    font:700 14px ui-monospace,Menlo,Consolas,"DejaVu Sans Mono",monospace;text-transform:uppercase;letter-spacing:1px;cursor:pointer;
                    box-shadow:5px 5px 0 #444;transition:none}
            .fc-btn:active{background:#fff;color:#000;transform:translate(3px,3px);box-shadow:2px 2px 0 #444}
            .fc-app{width:32px;height:32px;flex:none;image-rendering:pixelated}
            .fc-btn-label{flex:1;text-align:start}
            .fc-btn-user{opacity:.75;font-weight:400;text-transform:none}
            .fc-foot{position:relative;margin-top:20px;font-size:11px;opacity:.5;text-align:center;letter-spacing:2px;text-transform:uppercase}
        `;
        (document.head || document.documentElement).appendChild(style);
    } catch (e) {
        log("W", "could not add About Us styles: " + e);
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

    const registerAbout = () => {
        try {
            const h = Vencord.Webpack.Common.React.createElement;
            const settings = Vencord.Plugins.plugins.Settings;
            if (!settings?.customEntries) throw new Error("Settings plugin has no customEntries");
            if (settings.customEntries.some(e => e.key === "fyocord_about")) return;
            settings.customEntries.push({
                key: "fyocord_about",
                title: "About Us",
                panelTitle: "About Us",
                Component: () => window.__fcAbout(h),
                Icon: AboutIcon(h)
            });
            log("I", "About Us page registered");
        } catch (e) {
            log("W", "could not add the About Us page: " + (e && e.message ? e.message : e));
        }
    };

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
            registerAbout();

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
