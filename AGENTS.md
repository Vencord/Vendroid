An android app that uses Webview to load discord.com and injects Vencord

- Run `serial=$(scripts/launchEmulator)` to start or reuse an emulator and wait for Android to finish booting. Run emulator ADB commands with `adb -s "$serial"`.
- Open Vendroid Debug with `adb -s "$serial" shell am start -n dev.vendicated.vencord.dbg/dev.vendicated.vencord.MainActivity`.
- Run `scripts/ensureLoggedIn` after opening the debug app. It checks the Discord session and uses `DISCORD_TOKEN` from the repository `.env` if login is needed. If the script reports a missing token, ask the user to add it to `.env`.
- Use Chrome DevTools or `scripts/cdp.mjs` to debug the WebView. For example, run `scripts/cdp.mjs eval 'location.href'`, pipe JavaScript to `scripts/cdp.mjs eval -`, or run `scripts/cdp.mjs send Page.reload`. The CDP and login scripts use Node.js 24.
- Javascript code inside the webview can interact with Discord & Vencord, put it in `app/src/main/res/raw/vendroid_script.js`
- Vencord exposes the entire API as `window.Vencord`, explore this
- Read Vencord code when relevant. You can probably find it cloned at `../Vencord` or `../vencord`. If not, you can clone it yourself from https://github.com/Vendicated/Vencord.

After finishing changes, use `./gradlew installDebug` to install the latest debug app to my phone
