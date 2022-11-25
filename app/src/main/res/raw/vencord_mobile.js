!(() => {
const ModalEscapeHandler = Vencord.Util.lazyWebpack(m => m.binds?.[0] === "esc" && m.binds.length === 1);
const EscapeHandler = Vencord.Util.lazyWebpack(m => m.binds?.[0] === "esc" && m.binds[1] === "shift+pagedown");
window.VencordMobile = {
    onBackPress() {
        // false implies modal closed
        if (ModalEscapeHandler.action() === false) return;

        EscapeHandler.action({target:document.activeElement});
    }
}
})();