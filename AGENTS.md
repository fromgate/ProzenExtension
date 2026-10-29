# AGENTS.md

## Project

Kotlin/JS (IR) browser extension for the Dzen editor (`https://dzen.ru/profile/editor/*`). Every module targets `js(IR)` and uses only `jsMain`; there is no JVM/Android target. UI text and most comments are Russian.

## Build and verification

- `./gradlew :prozen:buildExtension` — unpacked Chrome/Edge extension in `build/extension`.
- `./gradlew :prozen:packageExtension` — Chrome/Edge zip in `build/extension-zip/prozen.zip`.
- `./gradlew :prozen-firefox:packageExtension` — Firefox extension in `build/extension-firefox` and `build/extension-zip/prozen-firefox.zip`. It depends on `:prozen:packageExtension`, so it also regenerates the Chrome zip; root `./gradlew build` builds both variants.
- `./gradlew :<module>:compileKotlinJs` — fastest compile check, e.g. `./gradlew :content:compileKotlinJs`. It also compiles project dependencies.
- There is no test suite, lint/format config, or CI workflow. Verify by compiling and loading `build/extension` (Chrome/Edge) or `build/extension-firefox` (Firefox) manually.
- Do not run `./gradlew buildVersion` unless releasing: it packages both variants, then `:prozen:tagVersion` creates and pushes a `vX.Y.Z` git tag.

## Versioning and manifests

- The extension version is the first `### X.Y.Z` line in `CHANGELOG.md` (parsed by `getVersionFromChangelog()` in `prozen/build.gradle.kts`). Add the new changelog entry at the top before building a release.
- `:prozen:updateManifest` runs for both `buildExtension` and packaging and rewrites `"version"` in `prozen/src/jsMain/resources/manifest.json` and `prozen-firefox/src/jsMain/resources/manifest.json`. Do not bump manifest versions by hand; build tasks may modify these tracked files.
- The Chrome and Firefox manifests are separate and checked in. When adding permissions, resources, or content-script matches, update both. Chrome uses an MV3 `service_worker`; Firefox uses `background.scripts` plus a gecko id.
- Firefox detection is `isFirefox()`: it checks whether the manifest description contains `"Firefox"` (from `extDescriptionFirefox`). Do not change that wording without updating the check.

## Module map

- Shared/library modules: `chrome/` (hand-maintained `chrome.*` externs, patches in `fix.*.kt`; no regeneration script), `common/` (storage, options, cache, date/i18n, logging, UI components), `requester/` (Dzen `editor-api` client; `RequesterCached` adds `chrome.storage.local` caching), `page-analyzer/`, `page/`, `database/` (IndexedDB scaffold, not imported by other modules yet).
- Executable bundles: `content/`, `popup/`, `search/`, `status/`, `stats/`, `settings/`, `service-worker/`, each with `fun main()`.
- `prozen/` — Chrome/Edge packaging plus all static resources (manifests, HTML, CSS, icons, locales, XLSX library). It has no Kotlin sources.
- `prozen-firefox/` — Firefox manifest overlay only; its build reuses the `prozen` output.
- `page-analyzer/` — publication checks. Add a `PageCheck` object plus a `TypeCheck` enum entry, then register it in the default list in `createPageContext()` (`PageContextBuilder.kt`).

## Runtime wiring

- `contentKt.js` is the content script. It injects `js/pageKt.js`, receives `_csrfToken`/`_data` via `window.postMessage` (`prozen-data`), then dispatches by URL through `getPageType()` in `Content.kt` to `Article`, `Brief`, `Video`, `Shorts`, `Studio`, or `Menu`.
- `ServiceWorker.kt` intercepts only two Dzen endpoints and forwards request parameters/token to the content script. Its own requests carry `X-Prozen-Request` to avoid being re-intercepted. The content script pings it every 25 seconds to keep it alive.
- Extension pages are static HTML in `prozen/src/jsMain/resources/`. Each loads `js/<name>Kt.js`; the filename comes from `outputFileName` in that module's `build.gradle.kts`, so keep HTML and bundle names in sync.
- `Requester` calls Dzen APIs with a mobile UA, Zen headers, and `X-Csrf-Token`. Content pages cache `prozenToken` and `prozenPublisherId` in `chrome.storage.local` for popup/search/stats/status.
- Add settings as `Option` entries in `common/src/jsMain/kotlin/common/Options.kt`. `Settings.kt` renders every option automatically except `group = "hidden"`; it also hardcodes a `firefoxRelated` set that is hidden on Firefox.
- UI strings go through `common/M.kt` (locale key = property name) or `data-i18n`; add keys to `_locales/ru/messages.json` (default locale) and `_locales/en/messages.json`.

## Conventions and gotchas

- Dependency versions live in `buildSrc/src/main/kotlin/Libs.kt`; the Kotlin plugin version is pinned in the root `build.gradle.kts`.
- New KMP JS modules are packaged automatically by `getJsSourceModules()`. Add a module to `DISALLOWED_MODULES` in `buildSrc/src/main/kotlin/Modules.kt` if it is not an executable bundle; `database` is currently packaged despite being unused.
- Gradle output directories are **not** ignored by git. Never commit `build/`, `.gradle/`, `buildSrc/build/`, or per-module `build/` directories, and stage explicit paths rather than `git add -A`.
- `.gitignore` ignores `*.zip`, `*.txt`, and `*.sh`; force-add such files if they are intentionally tracked. `.work/` is ignored scratch/archive material and is not part of the build.
