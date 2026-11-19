!(() => {
    const {
        Webpack: {
            findLazy,
            onceReady,
            Common
        },
        Components: { Button, Flex, WebsiteIcon },
        Plugins: {
            plugins: { Settings: SettingsPlugin }
        }
    } = Vencord;

    const ModalEscapeHandler = findLazy(m => m.binds?.length === 1 && m.binds[0] === "esc");

    let isSidebarOpen = false;

    function VendroidSettings() {
        const { React } = Common;

        return React.createElement(
            Flex,
            { flexDirection: "column" },
            React.createElement(
                Button,
                { onClick: () => VendroidNative.openVendroidSettings() },
                "Open Vendroid Settings"
            ),
            React.createElement(
                Button,
                { onClick: () => VendroidNative.openVencordUpdater() },
                "Open Updater"
            )
        );
    }
    SettingsPlugin.customEntries.push({
        key: "vendroid_settings",
        title: "Vendroid",
        Icon: WebsiteIcon,
        Component: VendroidSettings,
    });

    onceReady.then(() => {
        const { FluxDispatcher } = Common;

        FluxDispatcher.subscribe("MOBILE_WEB_SIDEBAR_OPEN", () => {
            isSidebarOpen = true;
        });
        FluxDispatcher.subscribe("MOBILE_WEB_SIDEBAR_CLOSE", () => {
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
            textContent: VendroidNative.getVencordCss()
        })
    ), { once: true });
})();
