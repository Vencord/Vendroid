!(() => {
const Bruh = Vencord.Util.lazyWebpack(m => m.emitter?._events?.SCROLLTO_PRESENT)
const ModalEscapeHandler = Vencord.Util.lazyWebpack(m => m.binds?.[0] === "esc" && m.binds.length === 1);
const EscapeHandler = Vencord.Util.lazyWebpack(m => m.binds?.[0] === "esc" && m.binds[1] === "shift+pagedown");
window.VencordMobile = {
    onBackPress() {
        // false implies modal closed
        if (ModalEscapeHandler.action() === false) return;

        let hadEffect = true;
        const onScroll = () => hadEffect = false;
        Bruh.subscribe("SCROLLTO_PRESENT", onScroll);
        // DISCORD IS BRAINDEAD AND ALWAYS RETURNS FALSE so we need to do the hack where we check if the
        // event was dispatched (aka end was reached)
        EscapeHandler.action({target:document.activeElement});
        setTimeout(() => {
            Bruh.unsubscribe("SCROLLTO_PRESENT", onScroll);
            if (!hadEffect) {
                VencordMobileNative.goBack();
            }
        }, 50)
    }
}
})();