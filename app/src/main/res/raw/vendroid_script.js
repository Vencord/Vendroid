!(() => {
    const { findLazy, Common, onceReady } = Vencord.Webpack;
    const ModalEscapeHandler = findLazy(m => m.binds?.length === 1 && m.binds[0] === "esc");

    let isSidebarOpen = false;

    function VendroidSettings() {
        const { React } = Common;
        return React.createElement(
            "div",
            { style: { display: "flex", flexDirection: "column", alignItems: "flex-start", gap: 8 } },
            React.createElement(
                Vencord.Components.Button,
                { onClick: () => VencordMobileNative.openVendroidSettings() },
                "Open Vendroid Settings"
            ),
            React.createElement(
                Vencord.Components.Button,
                { onClick: () => VencordMobileNative.openVencordUpdater() },
                "Open Updater"
            )
        );
    }
    Vencord.Plugins.plugins.Settings.customEntries.push({
        key: "vendroid_settings",
        title: "Vendroid",
        Component: VendroidSettings,
        Icon: Vencord.Components.WebsiteIcon
    });

    onceReady.then(() => {
        Common.FluxDispatcher.subscribe("MOBILE_WEB_SIDEBAR_OPEN", () => {
            isSidebarOpen = true;
        });
        Common.FluxDispatcher.subscribe("MOBILE_WEB_SIDEBAR_CLOSE", () => {
            isSidebarOpen = false;
        });
    });

    window.VencordMobile = {
        // returns true if an action was taken, false if the java side should handle the back press
        onBackPress() {
            // false means modal closed (no further action rewuired?)
            if (ModalEscapeHandler.action() === false) return true;

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

    document.addEventListener("DOMContentLoaded", () => document.documentElement.appendChild(
        Object.assign(document.createElement("style"), {
            textContent: VencordMobileNative.getVencordCss()
        })
    ), { once: true });
})();
