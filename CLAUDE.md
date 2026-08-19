# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
  `PasteToOpenAction.pasteFromClipboard()` calls `openWithSettings()` itself. It does not route through
  `OmeZarrIOPlugin`, and deliberately so: it adds clipboard reading (`ClipboardUtils`), user-facing error messages via
  its `errorHandler`, and the `s3:` bypass below — none of which fit the `IOPlugin` contract. Nothing in this repo calls
  `IOService` itself.

**`s3:` support is paste-only.** `ZarrUtils.isZarr` cannot probe `s3:` cheaply (see its javadoc), so
`PasteToOpenAction` skips the check for that scheme and opens directly. `OmeZarrIOPlugin` therefore declines `s3:` (as
it always has), and `fiji://…?p=s3://…` cannot be fixed from our side either: fiji-links' `open/url` branch dies in
`new URL(p)` with `MalformedURLException: unknown protocol: s3`, and its `open/source` branch has
`LocationService.resolve` fall back to a bogus *relative* `FileLocation` (`file:/<cwd>/s3:/bucket/…`). Giving links s3
parity would need an s3 `Location`/`DataHandle` plugin on the classpath, not a change here.

**`fiji://` links need no code of ours.** Fiji-Latest ships `sc.fiji:fiji-links` (verified present in a Fiji-Latest
`jars/` alongside `scijava-desktop` and `scijava-io-http`). Its `OpenLinkHandler` owns the
`fiji://open/{file,url,source}?p=…` syntax, the OS-level scheme registration, and the URI parsing, and finishes by
calling `IOService.open(Location)` – which dispatches to whichever `IOPlugin` claims the location, i.e. to
`OmeZarrIOPlugin`. So `fiji://` links honor the user's `ZarrOpenBehavior` for free. **Do not add a `LinkHandler` plugin
of our own**: it would need `org.scijava:scijava-desktop` (Java 11 bytecode, breaking the Java-8/Fiji-Stable baseline,
and its unresolvable plugin *type* string in the annotation index makes `DefaultPluginService` log `"1 exceptions
occurred during plugin discovery."` on every Fiji-Stable start), and it would compete with `fiji-links` for the same
URIs — `HandlerService.getHandler` returns the first match by priority, so which one wins would be arbitrary. See the
abandoned `add-link-handler` branch and issue #68 / PR #101 for that dead end.

**Core data model:** `PyramidBackend` is a single-method interface (`<T> PyramidContents<T> load(URI)`).
`AbstractPyramidBackend` implements `load` as a template method – try the multiscales group, fall back to a single
array (parent multiscales group first, then the array's own `dimension_names`) – and leaves three `protected abstract`
hooks for the reader-specific steps: `loadMultiscale`, `tryLoadLevelFromParent` and `tryLoadArrayNodeOnly` (the two
`try*` hooks return `null` for "not applicable, try the next"). `load` and `loadSingleArray` are `final`, so the order
is fixed for every backend. It is extended by `N5PyramidBackend` (N5-universe, OME-NGFF v0.3–v0.5) and
`ZarrJavaPyramidBackend` (`dev.zarr:zarr-java`, Zarr v2/v3). Both produce an immutable `PyramidContents<T>` holding the
per-level `CachedCellImg`s, affine transforms, axis calibration, and optional OMERO metadata, plus an `asImg()`
accessor.

Resolution levels are selected through `PyramidContents.suggestResolutionLevel(Integer preferredMaxWidth)`, which
returns `NO_MATCHING_LEVEL` (`-1`) rather than silently falling back when no level is narrow enough; the caller decides
(`ZarrOpener` offers `smallestResolutionLevel()` and asks). `asImg()`/`asLargestImg()`, `asSmallestImg()` and
`asImg(int)` name the levels explicitly.

The `tryLoadArrayNodeOnly` route can only invent a calibration – `AxisCalibration.createPlaceholderCalibration` builds
axes with scale `1.0` and an empty unit, because a bare array names its axes (Zarr v3 `dimension_names`) but not their
scale. Such contents are built through `PyramidContents.singleLevelWithPlaceholderCalibration(...)`, the only way to set
the `hasPlaceholderCalibration` flag, so the guess always travels with the image; `AbstractPyramidBackend` also logs a
warning. Every `ZarrOpener` display path refuses to show a flagged image unless the user confirms. An array whose axes
cannot be named at all (Zarr v2 without a readable parent) remains a hard `SingleArrayAxesUnknownException`.

`ZarrOpener` picks a backend (`ZarrReaderBackend`: N5 or ZARR_JAVA), loads and caches the `PyramidContents`,
and wraps it into either a `PyramidalDataset` (extends `DefaultDataset`, for ImageJ) or a `PyramidalBdv` (per-channel
BDV `SourceAndConverter` lists, volatile-wrapped per resolution level) – both implement the marker interface
`Pyramidal`.

**Opening modes** (enum `ZarrOpenBehavior` in `ome.zarr.fijiui.open.options`):

- `IMAGEJ_HIGHEST_RESOLUTION` / `IMAGEJ_CUSTOM_RESOLUTION` → `ZarrOpenActions.openIJWithImage()`
- `BDV_MULTI_RESOLUTION` → `ZarrOpenActions.openBDVWithImage()`
- `SHOW_SELECTION_DIALOG` → `DnDActionChooser` Swing dialog with icon buttons

**Settings** are persisted across Fiji sessions via SciJava `PrefService`, read/written through `ZarrOpeningSettings` (
open-behavior, preferred width, reader backend) and surfaced via the `OpeningBehaviorSettings` command.
`UserScriptSettings` currently only logs the chosen script path – it does not persist it.

**Active-window tracking:** `PyramidalService` (a SciJava service) tracks the most-recently-focused `Pyramidal` window (
BDV or ImageJ) via AWT focus listening; `PyramidalPreprocessor` auto-fills any `Pyramidal`-typed command parameter with
the currently active one.

**Key utility classes:**

- `ZarrUtils` – consolidated Zarr-detection utility; `isZarr(URI)` handles both local filesystem (looks for `.zarray` /
  `zarr.json`) and HTTP (HEAD-requests known metadata files); `isHttpAccessible` is package-private
- `ClipboardUtils` – reads the system clipboard (`readClipboard()`) and converts strings to URIs (
  `stringToUri(String, Consumer<String>)`); `readClipboardAsUri(Consumer<String>)` combines both
- `BdvUtils` – shows a `PyramidalBdv` in a BDV window, applies OMERO channel colors/display-ranges, wires
  reference-counting and window-close cleanup
- `Affine3DUtils` – checks whether an `AffineTransform3D`'s linear part is a pure axis-aligned scaling (no
  rotation/shear)
- `ScriptUtils` – opens Fiji script editor with a pre-populated scriptlet

Note: `BdvHandleService` is test/example-only now (`ome-zarr-fiji-ui/src/test/java/ome/zarr/examples/demo/`), not part
of the shipped plugin.

## Modules

Multi-module reactor. The root `pom.xml` is the aggregator (`ome.zarr:ome-zarr-parent`, packaging `pom`) and inherits
`pom-scijava`. Each module lives in its own directory `ome-zarr-<name>/` with its own `pom.xml` and carries its own
SciJava provenance (required by the enforcer). Five published modules:

- **`ome-zarr-imglib2`** – package `ome.zarr.imglib2` (+`.metadata`, `.exceptions`); backend-agnostic core (
  `PyramidBackend`, `AbstractPyramidBackend`, `PyramidContents`, `ZarrUtils`, `Affine3DUtils`). No Fiji or backend
  dependency.
- **`ome-zarr-n5`** – `ome.zarr.n5` (`N5PyramidBackend`); depends on imglib2 + external N5-universe (codecs `n5-zarr`/
  `n5-blosc`/zstd arrive transitively via `n5-universe`).
- **`ome-zarr-zarrjava`** – `ome.zarr.zarrjava` (`ZarrJavaPyramidBackend`); depends on imglib2 + `dev.zarr:zarr-java`.
- **`ome-zarr-fiji`** (+`.open`, `.plugins`, `.util`) – ImageJ/BDV integration (`ZarrOpener`, `PyramidalDataset`,
  `PyramidalBdv`, `PyramidalService`, `BdvUtils`). Depends on imglib2 only (no backend artifact, and no N5 library at
  all outside test scope – the former `N5Utils.open()` single-scale fallback in `ZarrOpener` is gone, single arrays are
  loaded through the selected backend as one-level pyramids).
- **`ome-zarr-fiji-ui`** – `ome.zarr.fijiui` (+`.open`, `.open.options`, `.plugin`, `.settings`, `.dialog`, `.util`);
  the OME-Zarr `IOPlugin` (drag-and-drop and `fiji://` links), SciJava commands, dialogs, opening-behavior settings.
  Depends on all four other modules – the batteries-included artifact.

Dependency graph: `n5`, `zarrjava`, `fiji` each → `imglib2`; `fiji-ui` → {`imglib2`, `n5`, `zarrjava`, `fiji`}. Backends
are selected at runtime (`ZarrReaderBackend`), so `fiji` needs at least one backend on the classpath at runtime even
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
