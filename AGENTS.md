An android app that uses Webview to load discord.com and injects Vencord

- Use android emulator for testing. The WebView is debuggable, use Chrome devtools to debug it.
- Javascript code inside the webview can interact with Discord & Vencord, put it in `app/src/main/res/raw/vendroid_script.js`
- Vencord exposes the entire API as `window.Vencord`, explore this
- Read Vencord code when relevant. You can probably find it cloned at `../Vencord` or `../vencord`. If not, you can clone it yourself from https://github.com/Vendicated/Vencord.

After finishing changes, use `./gradlew installDebug` to install the latest debug app to my phone