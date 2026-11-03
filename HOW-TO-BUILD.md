# How to build the extension from sources

## Requirements

- **JDK 17 or newer** (the build is verified with JDK 21). `java -version` must report 17+.
- **Internet access for the first build**: the Gradle wrapper downloads Gradle 8.14.4, and the Kotlin plugin downloads Node.js/Yarn plus the npm dependencies. Subsequent builds work offline.
- OS: Linux, macOS, or Windows (`gradlew` and `gradlew.bat` are both included).

No separate Gradle, Node.js, or Yarn installation is required — the build fetches its own toolchain.

## Building

From the source directory run:

```bash
./gradlew buildRelease          # Linux / macOS
gradlew.bat buildRelease        # Windows
```

This builds the release version of the extension for three browsers:

- unpacked directories ready to load — `build/extension/prozen-chrome/`,
  `build/extension/prozen-edge/`, `build/extension/prozen-firefox/`;
- zip archives for store submission — `build/extension-zip/prozen-chrome.zip`,
  `prozen-edge.zip`, `prozen-firefox.zip`.

For a quick manual check you can load the unpacked extension: 
- in Chrome/Edge —
`chrome://extensions` → "Load unpacked" → `build/extension/prozen-chrome`;
- in Firefox —
`about:debugging` → "This Firefox" → "Load Temporary Add-on" → `manifest.json` from
`build/extension/prozen-firefox`.

## Notes
- The extension version is taken from the first `### X.Y.Z` heading in `CHANGELOG.md`.
