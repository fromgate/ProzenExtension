# AGENTS.md

## Project

Kotlin/JS (IR) browser extension for the Dzen editor (`https://dzen.ru/profile/editor/*`). Every module targets `js(IR)` and uses only `jsMain`; there is no JVM/Android target. UI text and most comments are Russian.

## Build and verification

The build has two modes, chosen at configuration time in the root `build.gradle.kts`:

- `-Pprozen.dev=true|false` wins.
- Otherwise the `buildVersion` task means **release**, any other invocation means **dev**.

- `./gradlew build` — dev: unpacked extension in `build/extension-dev`, zips `build/extension-zip/prozen-dev.zip` and `prozen-firefox-dev.zip`, source maps on, `:devtools` source set `src/dev`.
- `./gradlew buildVersion` — **release**: `build/extension`, `prozen.zip`, `prozen-firefox.zip`, no source maps, `:devtools` uses `src/release` stubs, and `checkReleaseBundle` runs before tagging. With `-Pprozen.dev=true` it fails at configuration.
- `./gradlew -Pprozen.dev=false build` — release artifacts without creating/pushing a tag (useful for local checks).
- `./gradlew :prozen:buildExtension` / `:prozen:packageExtension` — honor the current mode; use them with the flag when checking a specific variant.
- `./gradlew :<module>:compileKotlinJs` — fastest compile check, e.g. `./gradlew :content:compileKotlinJs`. It also compiles project dependencies.
- `./gradlew -p buildSrc test` — JUnit tests for the `buildSrc` API-reference compiler. There is no test suite for extension code, no lint/format config, and no CI workflow. Verify by compiling and loading `build/extension-dev` (dev, Chrome/Edge) or `build/extension` (release), and `build/extension-firefox` (Firefox) manually.
- Do not run `./gradlew buildVersion` unless releasing: it packages both variants, then `:prozen:tagVersion` creates and pushes a `vX.Y.Z` git tag.

## Versioning and manifests

- The extension version is the first `### X.Y.Z` line in `CHANGELOG.md` (parsed by `getVersionFromChangelog()` in `prozen/build.gradle.kts`). Add the new changelog entry at the top before building a release.
- `:prozen:updateManifest` runs for both `buildExtension` and packaging and rewrites `"version"` in `prozen/src/jsMain/resources/manifest.json` and `prozen-firefox/src/jsMain/resources/manifest.json`. Do not bump manifest versions by hand; build tasks may modify these tracked files.
- The Chrome dev manifest is produced at build time from the release manifest (name `Prozen dev`, `unlimitedStorage`); the tracked manifest stays release-shaped. Firefox dev uses the normal manifest and only differs in JS and zip name.
- The Chrome and Firefox manifests are separate and checked in. When adding permissions, resources, or content-script matches, update both. Chrome uses an MV3 `service_worker`; Firefox uses `background.scripts` plus a gecko id.
- Firefox detection is `isFirefox()`: it checks whether the manifest description contains `"Firefox"` (from `extDescriptionFirefox`). Do not change that wording without updating the check.

## Module map

- Shared/library modules: `chrome/` (hand-maintained `chrome.*` externs, patches in `fix.*.kt`; no regeneration script), `common/` (storage, options, cache, date/i18n, logging, UI components), `requester/` (Dzen `editor-api` client; `RequesterCached` adds `chrome.storage.local` caching), `page-analyzer/`, `page/`, `database/` (IndexedDB scaffold, not imported by other modules yet), `devtools/` (dev-only API validator; real code in `src/dev/kotlin`, no-op stubs in `src/release/kotlin`, selected by `prozenDev`; library, no bundle of its own).
- Executable bundles: `content/`, `popup/`, `search/`, `status/`, `stats/`, `settings/`, `service-worker/`, each with `fun main()`.
- `prozen/` — Chrome/Edge packaging plus all static resources (manifests, HTML, CSS, icons, locales, XLSX library). It has no Kotlin sources.
- `prozen-firefox/` — Firefox manifest overlay only; its build reuses the `prozen` output.
- `page-analyzer/` — publication checks. Add a `PageCheck` object plus a `TypeCheck` enum entry, then register it in the default list in `createPageContext()` (`PageContextBuilder.kt`).

## Runtime wiring

- `contentKt.js` is the content script. It injects `js/pageKt.js`, receives `_csrfToken`/`_data` via `window.postMessage` (`prozen-data`), then dispatches by URL through `getPageType()` in `Content.kt` to `Article`, `Brief`, `Video`, `Shorts`, `Studio`, or `Menu`.
- Dev bundles call `installDevValidator()` first in `main()` (`content`, `search`, `stats`, `status`) before creating `Requester`; `settings` adds the dev section via `devSettingsSection(this)`. In release these calls resolve to no-op stubs. The dev section renders two buttons (visible only with `Option.DEBUG`): «Скопировать расхождения (N)» copies the «почти TOML» built by `CopyText.kt`, «Скопировать схему API (Новое: X)» copies the full reference text embedded as `ZEN_API_REFERENCE_TOML`, with uncovered `new` paths patched into `[endpoints.<name>.unknown]` by `SchemaCopy.kt`. `refreshDevSettingsIndicator` keeps both in sync with the `D+Numpad0` debug toggle. The validator (`devtools/src/dev/kotlin`) accumulates diffs under `storage.local` keys `diff:<hash>` and stores the embedded reference hash under `prozen-api-reference-hash`; reference changes trigger revalidation. `unknown` entries cover their subtree like `ignored`, are forbidden to use `*`/`**`, and participate in `new` coverage but not in `missing`/`type`/`empty`. `types` and `view` are significant (not volatile); `diff` records also keep the canonical request minus `volatileParams`.
- `ServiceWorker.kt` intercepts only two Dzen endpoints and forwards request parameters/token to the content script. Its own requests carry `X-Prozen-Request` to avoid being re-intercepted. The content script pings it every 25 seconds to keep it alive.
- Extension pages are static HTML in `prozen/src/jsMain/resources/`. Each loads `js/<name>Kt.js`; the filename comes from `outputFileName` in that module's `build.gradle.kts`, so keep HTML and bundle names in sync.
- `Requester` calls Dzen APIs with a mobile UA, Zen headers, and `X-Csrf-Token`. Content pages cache `prozenToken` and `prozenPublisherId` in `chrome.storage.local` for popup/search/stats/status.
- Add settings as `Option` entries in `common/src/jsMain/kotlin/common/Options.kt`. `Settings.kt` renders every option automatically except `group = "hidden"`; it also hardcodes a `firefoxRelated` set that is hidden on Firefox.
- UI strings go through `common/M.kt` (locale key = property name) or `data-i18n`; add keys to `_locales/ru/messages.json` (default locale) and `_locales/en/messages.json`.

## Conventions and gotchas

- Dependency versions live in `buildSrc/src/main/kotlin/Libs.kt`; the Kotlin plugin version is pinned in the root `build.gradle.kts`.
- New KMP JS modules are packaged automatically by `getJsSourceModules(dev)`. Add a module to `DISALLOWED_MODULES` in `buildSrc/src/main/kotlin/Modules.kt` if it is not an executable bundle (`devtools` is there); `DEV_ONLY_MODULES` lists modules that exist only in dev, and `database` is currently packaged despite being unused.
- `devtools` picks `src/dev/kotlin` or `src/release/kotlin` by `prozenDev`; only one is on the source path. In dev, `:devtools:generateApiReference` writes `devtools/build/generated/reference/ZenApiReference.kt` from `reference/zen-api.toml`; the task does not exist in release.
- Gradle output directories are **not** ignored by git. Never commit `build/`, `.gradle/`, `buildSrc/build/`, or per-module `build/` directories, and stage explicit paths rather than `git add -A`.
- `.gitignore` ignores `*.zip`, `*.txt`, and `*.sh`; force-add such files if they are intentionally tracked. `.work/` is ignored scratch/archive material and is not part of the build.
