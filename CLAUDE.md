# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

It records **decisions and dead ends**, not an inventory of the code: if a fact is visible by reading the class, it
belongs in javadoc, not here. Each paragraph should answer "would a contributor do the wrong thing without this?"

## Working style

**A minimalism/YAGNI skill (e.g. `ponytail`) is welcome here and mostly agrees with this repo already.** Prefer the
laziest thing that works: reuse what is here, stdlib and platform before a dependency, deletion over addition, shortest
scoped diff, root cause over symptom. Much of what this file records *is* a YAGNI decision — the clearest one being the
`s3:` `LocationResolver` below: ~15 lines, technically easy, deliberately never written because the feature would be
half-supported. "Does this need to exist at all?" is the right first question, and here the answer was no.

Such a skill's rungs need a few repo-specific facts to come out right, because the lazy answer depends on what the code
has to support. These are *inputs* to the ladder, not exemptions from it:

- **The extension points are requested features, not speculative abstraction.** `ZarrOpener` exists so *third-party
  jars* can register an opener (issue #112); `PyramidBackend` so a backend can be switched at runtime. "No interface
  with one implementation" targets abstraction nobody asked for — these were asked for, so rung 1 is already answered.
  Same for `AbstractPyramidBackend`'s template method and `Pyramidal`.
- **Module and package boundaries are API, so merging them is a breaking change, not a shorter diff.** The five-module
  split is five published artifacts; opener and command discovery runs off the `@Plugin` annotation index; `PrefService`
  keys off a class's package. "Understand the problem before picking a rung" applies before consolidating any of it.
- **Design notes are explicitly requested output here, so they are not prose debt.** This file, commit bodies, and
  javadoc on non-obvious private helpers exist because the repo's history is how design decisions get reviewed. That
  is the "explanation the user explicitly asked for" carve-out, not an exception to it. Cutting duplicated
  restatements of code is always fine; cutting a reason, a rejected alternative, or a dead end is not.

One genuine override: **do not add tests unprompted — offer them instead.** This repo does not want the "leave one
runnable check behind" reflex; the existing suites are where tests go when asked for.

## What this project is

A Fiji/ImageJ plugin that provides drag-and-drop support for opening OME-Zarr (Open Microscopy Environment – Zarr) image
datasets. It supports OME-Zarr v0.4 (Zarr v2) and v0.5 (Zarr v3) and integrates with both ImageJ and BigDataViewer for
multi-resolution visualization.

## Build and test commands

**Java baseline: 8.** The `pom-scijava` 45 parent defaults `maven.compiler.release` to 11, but the root `pom.xml`
overrides it back to 8 (`scijava.jvm.version=8`, `scijava.jvm.build.version=[1.8.0-101,)`) so the plugin keeps running
on **both Fiji-Stable (Java 8) and Fiji-Latest (Java 21)**. Targeting release 8 requires that every dependency also be
release-8 bytecode — the `EnforceBytecodeVersion` enforcer rule fails the build otherwise. pom-scijava 45 resolves some
BigDataViewer artifacts at release 11, so the root pom pins Java-8-compatible versions:
`bigdataviewer-core.version=10.6.8` and `bigdataviewer-vistools.version=1.0.0-beta-36`. If you bump either (or the
pom-scijava parent) and the enforcer reports a "Banned Dependency … bytecode version" for a new transitive artifact,
either pin an older release-8 build of it or drop the Java-8 override and accept Java-11-only (Fiji-Latest) support.

This is a multi-module Maven reactor; commands run at the repo root operate on all modules.

Be careful with running the full test suite: it takes about 2 minutes on a 2025 MacBook Pro. Thus try to run single
tests or test classes first and the full suite only when really necessary.

```bash
mvn clean package                                     # build all modules
mvn test                                              # run all tests across all modules (4 GB heap – configured in pom.xml)
mvn -pl ome-zarr-fiji test -Dtest=ClassName           # single test class (scope to its module)
mvn -pl ome-zarr-fiji test -Dtest=ClassName#methodName  # single test method
mvn -pl ome-zarr-n5 -am test                          # build & test one module plus its upstream modules
mvn clean verify -Pcoverage                           # aggregated JaCoCo coverage → ome-zarr-coverage-report/target/site/jacoco-aggregate/jacoco.xml
```

Single-class/method runs are scoped to the owning module with `-pl` (a bare `-Dtest=` at the reactor root fails in
modules that lack the class). `-am` ("also make") builds the upstream modules a `-pl` target depends on without a prior
`mvn install`.

Blosc native library is required for tests. On macOS:

```bash
brew install c-blosc
export DYLD_LIBRARY_PATH=$(brew --prefix c-blosc)/lib:$DYLD_LIBRARY_PATH
# both flags are needed: n5-blosc loads libblosc via JNA (jna.library.path), and
# macOS SIP strips DYLD_LIBRARY_PATH from the forked test JVM
export JAVA_TOOL_OPTIONS="-Djava.library.path=$(brew --prefix c-blosc)/lib -Djna.library.path=$(brew --prefix c-blosc)/lib"
```

## Architecture

**Two independent entry paths**, both ending in `ZarrOpenActions.openWithSettings()`:

- **Via SciJava `IOService`** (drag-and-drop, `fiji://` links): `OmeZarrIOPlugin` – an `IOPlugin` that claims any
  `Location` whose URI passes `ZarrUtils.isZarr(URI)`. It accepts both `FileLocation` (drag-and-drop) and remote
  locations (`HTTPLocation`/`URLLocation`); `Location`s with no URI (`Location.getURI()` returns `null`, e.g.
  `BytesLocation`) are declined.
- **Directly, bypassing `IOService`** (clipboard paste – menu command, toolbar button, Ctrl/Cmd+Shift+V):
  `PasteToOpenAction.pasteFromClipboard()` calls `openWithSettings()` itself, as does `OpenOmeZarrCommand`
  (`File > Import > OME-Zarr...`, a folder chooser — issue #40) and `OpenOmeZarrArchiveCommand`
  (`File > Import > OME-Zarr Archive (.ozx)...`, a file chooser), which share `OmeZarrOpener`. **Do not merge them
  into one input**: a Swing file chooser is directories-only or files-only, never both (`FILE_AND_DIRECTORY_STYLE`
  reaches `SwingFileWidget`'s drop filter but not its Browse button), and a custom `InputWidget` did not fix it.
  Paste deliberately does not route through `OmeZarrIOPlugin`: it adds clipboard reading, user-facing error messages via
  its `errorHandler`, and the `s3:` bypass below — none of which fit the `IOPlugin` contract. Nothing in this repo calls
  `IOService` itself.

One entry point deliberately does **not** end in `openWithSettings()`: `OpenOmeZarrAsDatasetCommand`
(`Plugins > OME-Zarr > Open OME-Zarr as Dataset`, issue #40) takes the location as a single `String` input and declares
a `PyramidalDataset` output, so scripts can capture the image. It cannot honor the chosen `ZarrOpener` — BDV and the
selection dialog produce no `Dataset` — so it always reads one, via `ZarrReader.getPyramidalDataset()` with the
persisted backend and preferred width, which displays nothing. Being plain text rather than a chooser, it accepts
`http(s):` too, but *not* `s3:`.

`ZarrReader.openIJWithImage()`/`openBDVWithImage()` return what they showed (`null` on failure or when the user
declined) and `ZarrOpenActions` mirrors that; the plugin's own call sites want only the side effect, hence the
`@SuppressWarnings( "UnusedReturnValue" )` — the values exist for API and script users.

### `s3:` support is paste-only, deliberately

`ZarrUtils.isZarr` probes the local filesystem (`.zarray` / `zarr.json`) and HEAD-requests known metadata files over
HTTP, but **cannot probe `s3:`** (`ome-zarr-imglib2` has no AWS dependency at all, and an `s3://bucket/key` URI names
neither region nor endpoint; see its javadoc). So `PasteToOpenAction` skips the check for that scheme and opens
directly, passing a plain `java.net.URI` to `openWithSettings` — that path never touches SciJava's `Location` layer, and
the backend builds its own `S3Client`. Every other entry route validates with `isZarr` and therefore refuses `s3:`.

`fiji://…?p=s3://…` fails one layer above us, in the string→`Location` conversion that `OpenLinkHandler` does before it
can call `IOService.open(Location)`:

- `open/url` validates with `new URL(p)`, and `java.net.URL` (unlike `URI`) only accepts protocols it has a stream
  handler for → `MalformedURLException: unknown protocol: s3`, caught and logged inside fiji-links. We are never
  consulted. **Not fixable from here.**
- `open/source` calls `LocationService.resolve(p)`. Resolution is one `LocationResolver` plugin per scheme
  (`FileLocationResolver` for `file` in scijava-common, `HTTPLocationResolver` for `http(s)` in scijava-io-http) and
  none claims `s3`, so `resolve(URI)` returns `null` and `resolve(String)` falls back to treating the whole string as a
  *relative filename* → a bogus `file:/<cwd>/s3:/bucket/…`. `OmeZarrIOPlugin` is handed that, i.e. by then there is no
  `s3:` URI left to accept — which is why relaxing `isZarr` for `s3` would not help links at all.

The second one *is* fixable from here: we only ever use `Location.getURI()`, never a `DataHandle`, so a ~15-line
`LocationResolver` for `s3` returning `URILocation` would do it — both classes live in scijava-common, so no
scijava-desktop and no Java-8 breakage. **We still won't**: it would fix `open/source` while `open/url` keeps failing,
and half-working link support is worse for users than a clear "not supported". Revisit when fiji-links itself handles
non-`java.net` schemes (e.g. falling back to `LocationService.resolve` when `new URL(p)` throws); links then get `s3:`
for free, with no change here.

An installation without the AWS SDK on the classpath (Fiji-Stable) only notices when the first `s3:` URI is opened —
see `S3StoreFactory` under `ome-zarr-zarrjava` for the lazy-loading rule and the `S3SupportUnavailableException` it
throws so the user gets a "get Fiji-Latest" message instead of a linkage stack trace.

**`fiji://` links need no code of ours.** Fiji-Latest ships `sc.fiji:fiji-links` (verified present in a Fiji-Latest
`jars/` alongside `scijava-desktop` and `scijava-io-http`). Its `OpenLinkHandler` owns the
`fiji://open/{file,url,source}?p=…` syntax, the OS-level scheme registration, and the URI parsing, and finishes by
calling `IOService.open(Location)` – which dispatches to whichever `IOPlugin` claims the location, i.e. to
`OmeZarrIOPlugin`. So `fiji://` links honor the user's chosen `ZarrOpener` for free. **Do not add a `LinkHandler` plugin
of our own**: it would need `org.scijava:scijava-desktop` (Java 11 bytecode, breaking the Java-8/Fiji-Stable baseline,
and its unresolvable plugin *type* string in the annotation index makes `DefaultPluginService` log `"1 exceptions
occurred during plugin discovery."` on every Fiji-Stable start), and it would compete with `fiji-links` for the same
URIs — `HandlerService.getHandler` returns the first match by priority, so which one wins would be arbitrary. See the
abandoned `add-link-handler` branch and issue #68 / PR #101 for that dead end.

### Core data model

`PyramidBackend` has one real method (`<T> PyramidContents<T> read(URI)`) plus a `getName()` default that backends
override with their library's display name (`"N5"`, `"zarr-java"`) for user-facing messages. `AbstractPyramidBackend`
implements `read` as a template method – try the multiscales group, fall back to a single array (parent multiscales
group first, then the array's own `dimension_names`) – and leaves three `protected abstract` hooks for the
reader-specific steps: `readMultiscale`, `tryReadLevelFromParent` and `tryReadArrayNodeOnly` (the two `try*` hooks
return `null` for "not applicable, try the next"). `read` and `readSingleArray` are `final`, so the order is fixed for
every backend. Both `PyramidContents` and the per-level `CachedCellImg`s, transforms, calibration and optional OMERO
metadata it holds are immutable. Each backend also exposes a static `readPyramid(URI)` convenience entry point for
outside API users — not named `read`, because Java forbids a static method hiding an inherited instance method.

**A too-old reader library is reported, not thrown at the console.** A Fiji-Stable installation whose N5 stack predates
this plugin fails inside the backend with a `NoClassDefFoundError` (e.g. on `OmeNgffMetadataParser`), which is useless
to a user. `read` therefore catches it and rethrows `ReaderLibraryUnavailableException` (extends `StoreAccessException`,
exposes the missing class through `getMissingClass()`); `ZarrReader.showReaderLibraryUnavailable` names the backend and
the missing class and points at Fiji-Latest. The multiscale/single-array fallback lives in a private
`readMultiscaleOrSingleArray` for exactly this reason: a `NoClassDefFoundError` from `readSingleArray` inside the
`catch ( NotAMultiscaleImageException )` block would not be caught by that same `try`. The guard only spans `read` —
cell images are lazy, so a class missing solely on the chunk-read path still surfaces later, on a viewer thread. The
`s3:`-specific `S3SupportUnavailableException` is thrown deeper and converted before it ever reaches this guard.

`PyramidContents.suggestResolutionLevel(Integer preferredMaxWidth)` returns the `NO_MATCHING_LEVEL` sentinel rather
than silently falling back when no level is narrow enough; the caller decides (`ZarrReader` offers
`smallestResolutionLevel()` and asks).

The `tryReadArrayNodeOnly` route can only invent a calibration – `AxisCalibration.createPlaceholderCalibration` builds
axes with scale `1.0` and an empty unit, because a bare array names its axes (Zarr v3 `dimension_names`) but not their
scale. Such contents are built through `PyramidContents.singleLevelWithPlaceholderCalibration(...)`, the only way to set
the `hasPlaceholderCalibration` flag, so the guess always travels with the image; `AbstractPyramidBackend` also logs a
warning. Every `ZarrReader` display path refuses to show a flagged image unless the user confirms. An array whose axes
cannot be named at all (Zarr v2 without a readable parent) remains a hard `SingleArrayAxesUnknownException`.

`ZarrReader` picks a backend (`ZarrBackend`: N5 or ZARR_JAVA), reads and caches the `PyramidContents`, and wraps it into
either a `PyramidalDataset` (extends `DefaultDataset`, for ImageJ) or a `PyramidalBdv` (per-channel BDV
`SourceAndConverter` lists, volatile-wrapped per resolution level) – both implement the marker interface `Pyramidal`.

### Opening modes are an extension point, not an enum

(issue #112) `ZarrOpener` (in `ome.zarr.fiji.open`, module `ome-zarr-fiji`) is a plain `SciJavaPlugin`: any Fiji plugin
registers itself as an opening option with nothing but `@Plugin( type = ZarrOpener.class, name = …, label = …,
iconPath = … )` on a class in its own jar. **Deliberately no custom annotation**: `@Plugin` already carries
name/label/description/iconPath/priority, scijava-common's annotation processor indexes it for free in every downstream
jar, and `PluginInfo.getIconURL()` resolves the icon out of the *contributing* jar, which is what makes third-party
dialog icons work at all. A static mutable registry was the other candidate and loses: filling it needs startup code,
i.e. a SciJava plugin anyway.

- `ZarrOpener.open( ZarrOpenRequest )` – called off the EDT; `tooltip( request )` is an optional dynamic tooltip (only
  `ScriptEditorOpener` uses it, to name the configured script). Deliberately **no `supports( request )` pre-filter**: no
  shipped opener needs one, and only the selection dialog could honor it — the direct dispatch in `openWithSettings`
  runs whichever opener the user configured regardless — so a half-honored hook is worse than none. Adding a
  `default` method later is source- and binary-compatible, so it can arrive when a downstream opener needs it.
- `ZarrOpenRequest` – immutable, with a lazily built, cached `reader()`. Third-party openers use `uri()` and ignore
  `reader()`; ours go through it.
- `ZarrOpenerService` – `AbstractPTService<ZarrOpener>`: lists openers by priority, runs one by name, and resolves what
  a persisted setting means (`effectiveOpenerName`).
- Shipped openers – `imagej-preferred-resolution`, `imagej-highest-resolution`, `bdv-multi-resolution`,
  `n5-importer-dialog`, `n5-viewer-dialog`, `script-editor` – are one class each, next to the `ZarrOpenActions` they
  drive. Only the extension point itself sits in `ome-zarr-fiji`: that is the artifact a downstream opener compiles
  against, and it stays free of concrete openers. Help is a plain button, not an opener.

The selection dialog (`ZarrOpenActionChooser`, one icon button per offered opener) is **not** an opener: it opens
nothing, it *picks* one. The persisted setting is therefore a `String` that is either an opener `name` or the sentinel
`ZarrOpenerService.ASK`. Making the dialog an opener too, for one uniform list, would need a recursion guard – more
machinery for no user-visible gain.

**Who becomes the default** (issue #112): an explicit user choice always wins; the highest-`priority` opener only
takes over when *nothing* is persisted. Installing a plugin must never silently override a decision the user already
made. The shipped openers sit at `Priority.HIGH` and below, so a third party declares `Priority.VERY_HIGH` to be the
out-of-the-box default. A persisted name whose opener is gone (plugin uninstalled) logs and falls back to the
highest-priority one.

**Settings** are persisted across Fiji sessions via SciJava `PrefService`, read/written through `ZarrOpeningSettings`
(opener name, preferred width, reader backend – the backend defaults to `ZarrBackend.ZARR_JAVA`) and surfaced via the
`OpeningBehaviorSettings` command, whose choices are built from the registered openers rather than from a fixed list.
The pref key stays `"ZarrOpenBehavior"`, but the four names the removed `ZarrOpenBehavior` enum wrote are **not**
migrated: a migration map existed and was deliberately deleted, because adoption at 0.7 is small enough that carrying
it is not worth the code. A 0.7-and-earlier preference therefore reads as an unknown opener name and lands in the
"configured opener is not installed" fallback in `ZarrOpenActions.openWithSettings` – highest-priority opener, one
`info` log, nothing thrown – and the next visit to `OpeningBehaviorSettings` overwrites it. `getOpenerName()` returns
`null` for "never configured", which is what lets the priority rule above apply.

**Active-window tracking:** `PyramidalService` (a SciJava service) tracks the most-recently-focused `Pyramidal` window
(BDV or ImageJ) via AWT focus listening; `PyramidalPreprocessor` auto-fills any `Pyramidal`-typed command parameter
with the currently active one.

Note: `BdvHandleService` is test/example-only now (`ome-zarr-fiji-ui/src/test/java/ome/zarr/examples/demo/`), not part
of the shipped plugin.

## Modules

Multi-module reactor. The root `pom.xml` is the aggregator (`ome.zarr:ome-zarr-java`, packaging `pom`) and inherits
`pom-scijava`. Each module lives in its own directory `ome-zarr-<name>/` with its own `pom.xml` and carries its own
SciJava provenance (required by the enforcer). Five published modules:

**groupId is `ome.zarr`, matching the Java packages, and deployment goes to maven.scijava.org** – the two decisions are
one. The SciJava Nexus hosts any groupId, so `ome.zarr` needs no verification there; that is where 0.6.x and earlier
live (`https://maven.scijava.org/repository/releases/ome/zarr/…`) and where releases go again. What routes a release is
the root pom's `<releaseProfiles>sign,deploy-to-scijava</releaseProfiles>`: SciJava's `ci-build.sh` reads that property
and picks the target from it (`*deploy-to-scijava*` needs the `MAVEN_*` secrets, `*sonatype-oss-release*` the
`CENTRAL_*` ones – `.github/workflows/build.yml` passes both sets, so the unused ones are inert).

**Maven Central is not an option under this groupId** and that is the whole reason the project briefly published as
`sc.fiji:ome-zarr-fiji:0.3.x`: Central only accepts a deployment whose groupId sits in a namespace verified for the
publishing account. `sc.fiji` is verified for the SciJava account whose token the CI uses; `ome.zarr` is verified for
nobody and cannot be, since its reversed domain `zarr.ome` has no TLD to prove ownership of – a 0.7.x attempt failed
with `Namespace 'ome.zarr' is not allowed`. So going back to Central means switching every `<groupId>` again *and*
`releaseProfiles`; an org-owned namespace (`io.github.bioimagetools`, or `org.bioimagetools` if the domain is ever
registered) would be the alternative. Java package names stay `ome.zarr.*` through all of this.

- **`ome-zarr-imglib2`** – package `ome.zarr.imglib2` (+`.metadata`, `.exceptions`); backend-agnostic core. No Fiji or
  backend dependency.
- **`ome-zarr-n5`** – `ome.zarr.n5` (`N5PyramidBackend`, N5-universe, OME-NGFF v0.3–v0.5); depends on imglib2 +
  external N5-universe (codecs `n5-zarr`/`n5-blosc`/zstd arrive transitively via `n5-universe`).
- **`ome-zarr-zarrjava`** – `ome.zarr.zarrjava` (`ZarrJavaPyramidBackend`, Zarr v2/v3); depends on imglib2 +
  `dev.zarr:zarr-java`. It is the only backend that reads **zipped OME-Zarr archives (`.ozx`)**. An archive *is* the
  multiscale image: only a URI whose last segment ends in `.ozx` is one (`ZarrUtils.isOzxArchive`), so `…/img.ozx/0` is
  not addressable and fails like any other bad path. `resolveHandle` wraps it in a `ReadOnlyZipStore`, addressing the
  archive as a named entry of its parent store, not a store root — `HttpStore` appends a slash to a root and
  `GET /img.ozx/` is a 404. `ZarrUtils.isZarr` judges an archive by the file alone, since looking inside means reading
  the whole ZIP index. Other backends refuse archives up front: `N5PyramidBackend.openReader` throws
  `ZipArchiveUnsupportedException`, which `ZarrReader` turns into "switch the reader backend to zarr-java".

  Every AWS SDK reference lives in the package-private `S3StoreFactory` (the AWS SDK arrives transitively via
  zarr-java), so the SDK is loaded only when an `s3:` URI is actually opened; `file:`/`http(s):` datasets never touch
  it. Keep `ZarrJavaPyramidBackend` AWS-free — `catch` clauses included: a handler's exception type is resolved when
  the class is *verified*, not when the handler runs, so `catch ( SdkException e )` there would load AWS classes on
  every open. Hence `openMultiscaleImage` catches `RuntimeException` and delegates the `instanceof SdkException` test
  to `S3StoreFactory.isSdkException`, guarded by an `isS3( uri ) &&` short-circuit that keeps every other scheme from
  resolving that call. The flip side of lazy loading is that an installation without the AWS SDK (Fiji-Stable) only
  notices on the first `s3:` open: linking `S3StoreFactory` then throws `NoClassDefFoundError`. `createS3Store` catches
  that and throws `S3SupportUnavailableException` (in `ome-zarr-imglib2`, extends `StoreAccessException`), which
  `ZarrReader` turns into a "get Fiji-Latest" message plus a one-line warning instead of a linkage stack trace. That
  exception must be re-thrown ahead of the `catch ( RuntimeException e )` above, or its `isSdkException` call would fail
  to link too — on exactly the installation the message is about.
- **`ome-zarr-fiji`** (+`.read`, `.read.exceptions`, `.open`, `.plugins`, `.util`) – ImageJ/BDV integration and, in
  `.open`, the `ZarrOpener` extension point alone – a downstream opener depends on this module, not on `-ui`. It ships
  no opener of its own, so `ZarrOpenerService` names no default opener either: with an empty registry
  `effectiveOpenerName` returns `null` and `ZarrOpenActions` falls back to `ImageJPreferredResolutionOpener`. Depends on
  imglib2 only (no backend artifact, and no N5 library at all outside test scope – the former `N5Utils.open()`
  single-scale fallback in `ZarrReader` is gone, single arrays are read through the selected backend as one-level
  pyramids).
- **`ome-zarr-fiji-ui`** – `ome.zarr.fijiui` (+`.open`, `.open.openers`, `.open.options`, `.plugin`,
  `.plugin.command.*`, `.dialog`, `.util`); the OME-Zarr `IOPlugin` (drag-and-drop and `fiji://` links) in `.plugin`,
  Swing dialogs in `.dialog`, the six built-in `ZarrOpener`s in `.open.openers`. The SciJava commands sit in three
  sibling packages under `.plugin.command`, one per menu location: `.fileimport` for the two `File > Import` entries
  plus their shared `OmeZarrOpener` (package-private, so its tests live there too), `.tools` for the
  `Plugins > OME-Zarr` entries, and `.settings` for `OpeningBehaviorSettings` and `UserScriptSettings`. Commands are
  discovered through the `@Plugin` annotation index, not by package, so moving one between these packages does not
  touch its menu path – but `PrefService` keys off the class's package, so moving a class that is used as a preference
  key (as `ScriptUtils` uses `UserScriptSettings`) resets that stored setting. Depends on all four other modules – the
  batteries-included artifact.

Dependency graph: `n5`, `zarrjava`, `fiji` each → `imglib2`; `fiji-ui` → {`imglib2`, `n5`, `zarrjava`, `fiji`}. Backends
are selected at runtime (`ZarrBackend`), so `fiji` needs at least one backend on the classpath at runtime even
though it doesn't depend on one.

A sixth, non-published module **`ome-zarr-coverage-report`** only runs `jacoco:report-aggregate` to produce a
cross-module coverage report for SonarCloud; it joins the reactor solely under the `coverage` profile.

**Shared test code and resources** live once under `test-shared/` at the repo root, wired into every module by
`build-helper-maven-plugin` (`add-test-source`) and `<testResources>` in the parent pom:

- `test-shared/java/` – `ZarrTestUtils` (at the `ome.zarr` root) and `PyramidBackendTestBase` (at `ome.zarr.imglib2`),
  the shared backend test base subclassed by the n5/zarrjava/fiji suites.
- `test-shared/resources/` – sample OME-Zarr datasets (`ome/zarr/testdata/…`) and `logback-test.xml`.

`ZarrTestUtils.resourcePath()` resolves resources to a real filesystem `Path`, so they are copied into each module's
`target/test-classes` via the shared `<testResources>` (a test-jar would expose them only as `jar:` URLs, which
`Paths.get` rejects) – hence the shared-source approach rather than a published test-jar.