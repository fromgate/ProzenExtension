# AGENTS.md

## Project

Kotlin/JS (IR) browser extension for the Dzen editor (`https://dzen.ru/profile/editor/*`). Every module targets `js(IR)` and uses only `jsMain`; there is no JVM/Android target. UI text and most comments are Russian.

## Build and verification

The build has two modes, chosen at configuration time in the root `build.gradle.kts`:

- `-Pprozen.dev=true|false` wins.
- Otherwise the `buildVersion` task means **release**, any other invocation means **dev**.

- `./gradlew build` — dev: unpacked variants in `build/extension/prozen-{chrome,edge,firefox}-dev/`, zips `build/extension-zip/prozen-{browser}-dev.zip`, source maps on, `:devtools` source set `src/dev`. Stale release artifacts are removed (`build/extension/prozen-{browser}/`, release zips, `prozen-src.zip`).
- `./gradlew buildRelease` — **release**: `build/extension/prozen-{browser}/`, `prozen-{browser}.zip` for all three browsers plus `prozen-src.zip` (AMO source archive: sources under `prozen-src/` plus `HOW-TO-BUILD.md`; excludes dev code, the API reference, `docs/`). Dev and release cannot share one Gradle configuration (devtools source set, module list), so the dev variants are built by a nested `gradlew -Pprozen.dev=true build` first; `checkReleaseBundle` runs after packaging. No source maps, `:devtools` uses `src/release` stubs. `./gradlew -Pprozen.dev=false build` does the same.
- `./gradlew buildVersion` — same as the release build, then creates and pushes a `vX.Y.Z` git tag (`:prozen:tagVersion` runs `checkReleaseBundle` before tagging). With `-Pprozen.dev=true` it fails at configuration.
- `./gradlew :prozen:packageDevExtensions` / `:prozen:packageReleaseExtensions` — zips of the current mode's three browser variants; use them with the flag when checking a specific variant.
- `./gradlew :<module>:compileKotlinJs` — fastest compile check, e.g. `./gradlew :content:compileKotlinJs`. It also compiles project dependencies.
- `./gradlew -p buildSrc test` — JUnit tests for the `buildSrc` API-reference compiler and manifest builder. There is no test suite for extension code, no lint/format config, and no CI workflow. Verify by compiling and loading `build/extension/prozen-chrome-dev` (dev, Chrome/Edge), `build/extension/prozen-chrome` (release), or `build/extension/prozen-firefox[-dev]` (Firefox) manually.
- Do not run `./gradlew buildVersion` unless releasing: it packages both variants, then `:prozen:tagVersion` creates and pushes a `vX.Y.Z` git tag.

## Versioning and manifests

- The extension version is the first `### X.Y.Z` line in `CHANGELOG.md` (parsed by `getVersionFromChangelog()` in `buildSrc/src/main/kotlin/Functions.kt`). Add the new changelog entry at the top before building a release.
- `manifest.json` is a build artifact: `:prozen:generate{Chrome,Edge,Firefox}Manifest` generates it per variant (browser × dev/release) from `buildSrc/src/main/kotlin/ManifestBuilder.kt`. There are no tracked manifest files; never bump manifest versions by hand.
- Chrome/Edge dev manifests get the name `Prozen dev` (so the dev extension cannot be confused with the normal one) and `unlimitedStorage` (Chrome `storage.local` quota is 10 MB without it, and dev diffs can be large). Firefox dev uses the normal manifest and only differs in JS and zip name.
- Chrome/Edge and Firefox manifests are separate generated variants. When adding permissions, resources, or content-script matches, update both branches in `ManifestBuilder.kt`. Chrome/Edge use an MV3 `service_worker`; Firefox uses `background.scripts` plus a gecko id.
- Firefox detection is `isFirefox()`: it checks whether the manifest description contains `"Firefox"` (from `extDescriptionFirefox`). Do not change that wording without updating the check.

## Module map

- Shared/library modules: `chrome/` (hand-maintained `chrome.*` externs, patches in `fix.*.kt`; no regeneration script), `common/` (storage, options, cache, date/i18n, logging, UI components), `requester/` (Dzen `editor-api` client; `RequesterCached` adds `chrome.storage.local` caching), `page-analyzer/`, `page/`, `database/` (IndexedDB scaffold, not imported by other modules yet), `devtools/` (dev-only API validator; real code in `src/dev/kotlin`, no-op stubs in `src/release/kotlin`, selected by `prozenDev`; library, no bundle of its own).
- Executable bundles: `content/`, `popup/`, `search/`, `status/`, `stats/`, `settings/`, `service-worker/`, each with `fun main()`.
- `prozen/` — packaging for all browsers (chrome/edge/firefox variant dirs and zips in `build/extension[-zip]/`, `prozen-sources.zip` for Firefox AMO) plus all static resources (HTML, CSS, icons, locales, fonts, XLSX library). It has no Kotlin sources; `manifest.json` is not a resource anymore.
- `page-analyzer/` — publication checks. Add a `PageCheck` object plus a `TypeCheck` enum entry, then register it in the default list in `createPageContext()` (`PageContextBuilder.kt`).

## Runtime wiring

- `contentKt.js` is the content script. It injects `js/pageKt.js`, receives `_csrfToken`/`_data` via `window.postMessage` (`prozen-data`), then dispatches by URL through `getPageType()` in `Content.kt` to `Article`, `Brief`, `Video`, `Shorts`, `Studio`, or `Menu`.
- Dev bundles call `installDevValidator()` first in `main()` (`content`, `search`, `stats`, `status`) before creating `Requester`; `settings` adds the dev section via `devSettingsSection(this)`. In release these calls resolve to no-op stubs. The dev section renders one button (visible only with `Option.DEBUG`): «Скопировать схему API (Новое: X)» copies the full reference text embedded as `ZEN_API_REFERENCE_TOML`, with uncovered `new` paths patched into `[endpoints.<name>.unknown]` by `SchemaCopy.kt`. The text is prepared in advance (settings open, window focus) so the click can call the clipboard synchronously inside the user gesture; if the Clipboard API is missing or rejects, `DevTools.kt` falls back to a hidden textarea plus `execCommand('copy')` and reports the reason. `refreshDevSettingsIndicator` keeps the button in sync with the `D+Numpad0` debug toggle. The validator (`devtools/src/dev/kotlin`) accumulates diffs under `storage.local` keys `diff:<hash>` and stores the embedded reference hash under `prozen-api-reference-hash`; reference changes trigger revalidation. `unknown` entries cover their subtree like `ignored`, are forbidden to use `*`/`**`, and participate in `new` coverage but not in `missing`/`type`/`empty`. `types` and `view` are significant (not volatile); `diff` records also keep the canonical request minus `volatileParams`. Fields declared in `[endpoints.<name>.monitor]` get their values collected under `monitor:<hash>` keys: scalars as-is, arrays of scalars element-by-element, objects (or mixed arrays) as an `<object>`/`<array>` marker telling the developer to monitor concrete subfields; the schema-copy button merges collected values into `[endpoints.<name>.monitor.values]`.
- `ServiceWorker.kt` intercepts only two Dzen endpoints and forwards request parameters/token to the content script. Its own requests carry `X-Prozen-Request` to avoid being re-intercepted. The content script pings it every 25 seconds to keep it alive.
- Extension pages are static HTML in `prozen/src/jsMain/resources/`. Each loads `js/<name>Kt.js`; the filename comes from `outputFileName` in that module's `build.gradle.kts`, so keep HTML and bundle names in sync.
- `Requester` calls Dzen APIs with a mobile UA, Zen headers, and `X-Csrf-Token`. Content pages cache `prozenToken` and `prozenPublisherId` in `chrome.storage.local` for popup/search/stats/status.
- Add settings as `Option` entries in `common/src/jsMain/kotlin/common/Options.kt`. `Settings.kt` renders every option automatically except `group = "hidden"`; it also hardcodes a `firefoxRelated` set that is hidden on Firefox.
- UI strings go through `common/M.kt` (locale key = property name) or `data-i18n`; add keys to `_locales/ru/messages.json` (default locale) and `_locales/en/messages.json`.

## API validator (dev only)

- The reference is `reference/zen-api.toml`: one section per endpoint (`match`, `used`, `ignored`, `unknown`, optional `monitor`). Path syntax: `a.b.c`, `name[]`, `*`, `**` (last segment, `ignored` only), `\.` for a dot in a key name. Types in `used`: `string|number|boolean|object|array|null|any`, unions `A|B`, `?` marks optional; for arrays `?` means "absent or empty". Empty array under a strict `array` rule is an `empty` diff, under `array?` it is silent. `monitor` paths take the same types; `[endpoints.<name>.monitor.values]` holds the collected values (path -> array of strings), each path must be declared in `monitor`.
- Diff categories: `new`, `missing`, `type`, `status`, `unverified`, `empty`, `new-endpoint`, `new-variant`, `value` (a monitored value not yet in `monitor.values`; settles only when the updated reference records it). Entries self-clear when actual responses match again; when the embedded reference hash changes, stored diffs are re-evaluated; diffs of endpoints removed from the reference are marked orphaned, not deleted. See the runtime wiring bullet above for storage keys.
- Engine: `devtools/src/dev/kotlin/devtools/` — `PathSyntax`/`ActualRequest`/`Reference` (matching, compiled rules), `PathValidator` (JSON walk, categories, monitor collection), `DiffStore` (`chrome.storage.local`), `MonitorStore` (collected values), `ApiValidator` (Requester observer), `SchemaCopy` (byte-preserving TOML patcher), `DevTools` (wiring, schema button). The TOML compiler/validator is `buildSrc/src/main/kotlin/zenapi/`.
- `./gradlew verifyApiReference` validates `reference/zen-api.toml` without producing artifacts. The schema-copy patcher preserves the file byte layout (comments, section order, CRLF); round-trip must be stable: copy → save → rebuild → copy again yields the saved file. The generator fails on `"""` in the TOML (the text is embedded as `ZEN_API_REFERENCE_TOML`).
- Testing: JVM tests only (`buildSrc`); a JS test runner is intentionally not configured — verify JS changes by compiling and manual runs.

## Conventions and gotchas

- Dependency versions live in `buildSrc/src/main/kotlin/Libs.kt`; the Kotlin plugin version is pinned in the root `build.gradle.kts`.
- New KMP JS modules are packaged automatically by `getJsSourceModules(dev)`. Add a module to `DISALLOWED_MODULES` in `buildSrc/src/main/kotlin/Modules.kt` if it is not an executable bundle (`devtools` is there); `DEV_ONLY_MODULES` lists modules that exist only in dev, and `database` is currently packaged despite being unused.
- `devtools` picks `src/dev/kotlin` or `src/release/kotlin` by `prozenDev`; only one is on the source path. In dev, `:devtools:generateApiReference` writes `devtools/build/generated/reference/ZenApiReference.kt` from `reference/zen-api.toml`; the task does not exist in release.
- Gradle output directories are **not** ignored by git. Never commit `build/`, `.gradle/`, `buildSrc/build/`, or per-module `build/` directories, and stage explicit paths rather than `git add -A`.
- `.gitignore` ignores `*.zip`, `*.txt`, and `*.sh`; force-add such files if they are intentionally tracked. `.work/` is ignored scratch/archive material and is not part of the build.
