An android app that uses Webview to load discord.com and injects Vencord

- Use android emulator for testing. The WebView is debuggable, use Chrome devtools to debug it.
- Log in to Discord if necessary. You can check login status with `Vencord.Webpack.Common.AuthenticationStore.getId()` after Discord loaded. If unset, get the DISCORD_TOKEN value from `.env` and run `Vencord.Webpack.findByProps("loginToken").loginToken(token)` to log in. If this value isn't set, ask the user to enable NoDevtoolsWarning plugin, then run `Vencord.Webpack.Common.AuthenticationStore.getToken()` and add the `.env` file for you
- Javascript code inside the webview can interact with Discord & Vencord, put it in `app/src/main/res/raw/vendroid_script.js`
- Vencord exposes the entire API as `window.Vencord`, explore this
- Read Vencord code when relevant. You can probably find it cloned at `../Vencord` or `../vencord`. If not, you can clone it yourself from https://github.com/Vendicated/Vencord.

After finishing changes, use `./gradlew installDebug` to install the latest debug app to my phone