!(() => {
    const { findLazy, Common } = Vencord.Webpack;
    const ModalEscapeHandler = findLazy(m => m.binds?.length === 1 && m.binds[0] === "esc");

    window.VencordMobile = {
        onBackPress() {
            // false implies modal closed
            if (ModalEscapeHandler.action() === false) return;

            Common.FluxDispatcher.dispatch({ type: "MOBILE_WEB_SIDEBAR_OPEN" });
        }
    };
})();
