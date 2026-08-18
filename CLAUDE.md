# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

A Fiji/ImageJ plugin that provides drag-and-drop support for opening OME-Zarr (Open Microscopy Environment – Zarr) image datasets. It supports OME-Zarr v0.4 (Zarr v2) and v0.5 (Zarr v3) and integrates with both ImageJ and BigDataViewer for multi-resolution visualization.

## Build and test commands

**Java baseline: 8.** The `pom-scijava` 45 parent defaults `maven.compiler.release` to 11, but the root `pom.xml` overrides it back to 8 (`scijava.jvm.version=8`, `scijava.jvm.build.version=[1.8.0-101,)`) so the plugin keeps running on **both Fiji-Stable (Java 8) and Fiji-Latest (Java 21)**. Targeting release 8 requires that every dependency also be release-8 bytecode — the `EnforceBytecodeVersion` enforcer rule fails the build otherwise. pom-scijava 45 resolves some BigDataViewer artifacts at release 11, so the root pom pins Java-8-compatible versions: `bigdataviewer-core.version=10.6.8` and `bigdataviewer-vistools.version=1.0.0-beta-36`. If you bump either (or the pom-scijava parent) and the enforcer reports a "Banned Dependency … bytecode version" for a new transitive artifact, either pin an older release-8 build of it or drop the Java-8 override and accept Java-11-only (Fiji-Latest) support.

**Java-11-only optional feature: `scijava-desktop`.** `LinkHandlerPlugin` (the `fiji://` link handler in `ome-zarr-fiji-ui`) needs `org.scijava:scijava-desktop`, which is Java 11 bytecode throughout — it calls `Desktop.setOpenURIHandler`, a Java 9+ API. `ome-zarr-fiji-ui` nevertheless stays on release 8: javac's `-release 8` restricts the JDK platform API, not the class file version of classpath jars, and the `LinkHandler`/`AbstractLinkHandler`/`Links` signatures that `LinkHandlerPlugin` compiles against are all Java-8-shaped. The dependency is therefore declared `provided` (Fiji-Latest ships the jar in `jars/`; Fiji-Stable must not receive it transitively), and `ome-zarr-fiji-ui/pom.xml` re-declares the `enforceBytecodeVersion` rule with `org.scijava:scijava-desktop` excluded — `provided`, unlike `test`, is not in the rule's inherited `ignoredScopes`. At runtime on Fiji-Stable the plugin is simply skipped and everything else keeps working, at the cost of one cosmetic startup warning: SciJava's annotation index stores a plugin's *type* as a class-name string, and `DefaultPluginFinder.createInfo` resolves that string for every index entry while the `Context` is built. Without `scijava-desktop` this throws `ClassNotFoundException: org.scijava.desktop.links.LinkHandler` (note that `LinkHandlerPlugin` itself is never loaded — its unresolvable superclass is not what fails), `findPlugins` records the `Throwable` in its discovery-exceptions map, and `DefaultPluginService` logs `"1 exceptions occurred during plugin discovery."` — details only at debug level. There is no per-plugin opt-out in the index (only the global `scijava.plugin.blocklist` system property), so silencing it would take either registering the handler programmatically instead of by annotation, or an upstream change in scijava-common; the warning was deemed not worth either. Any future Java-11-only optional feature can follow the same pattern; only drop it if the required API stops being expressible in Java-8 signatures.

This is a multi-module Maven reactor; commands run at the repo root operate on all modules.

```bash
mvn clean package                                     # build all modules
mvn test                                              # run all tests across all modules (4 GB heap – configured in pom.xml)
mvn -pl ome-zarr-fiji test -Dtest=ClassName           # single test class (scope to its module)
mvn -pl ome-zarr-fiji test -Dtest=ClassName#methodName  # single test method
mvn -pl ome-zarr-n5 -am test                          # build & test one module plus its upstream modules
mvn clean verify -Pcoverage                           # aggregated JaCoCo coverage → ome-zarr-coverage-report/target/site/jacoco-aggregate/jacoco.xml
```

Single-class/method runs are scoped to the owning module with `-pl` (a bare `-Dtest=` at the reactor root fails in modules that lack the class). `-am` ("also make") builds the upstream modules a `-pl` target depends on without a prior `mvn install`.

Blosc native library is required for tests. On macOS:
```bash
brew install c-blosc
export DYLD_LIBRARY_PATH=$(brew --prefix c-blosc)/lib:$DYLD_LIBRARY_PATH
# both flags are needed: n5-blosc loads libblosc via JNA (jna.library.path), and
# macOS SIP strips DYLD_LIBRARY_PATH from the forked test JVM
export JAVA_TOOL_OPTIONS="-Djava.library.path=$(brew --prefix c-blosc)/lib -Djna.library.path=$(brew --prefix c-blosc)/lib"
```

## Architecture

**Entry point:** `DnDHandlerPlugin` – a SciJava `IOPlugin` that intercepts drag-and-drop of filesystem paths, checks whether the path is a Zarr folder via `ZarrUtils.isZarr(URI)`, then delegates to `ZarrOpenActions.openWithSettings()`.

**Core data model:** `PyramidBackend` is a single-method interface (`<T> PyramidContents<T> load(URI)`), implemented independently by `N5PyramidBackend` (N5-universe, OME-NGFF v0.3–v0.5) and `ZarrJavaPyramidBackend` (`dev.zarr:zarr-java`, Zarr v2/v3). Both produce an immutable `PyramidContents<T>` holding the per-level `CachedCellImg`s, affine transforms, axis calibration, and optional OMERO metadata, plus an `asImg()` accessor. `ZarrOpener` picks a backend (`ZarrReaderBackend`: N5 or ZARR_JAVA), loads and caches the `PyramidContents`, and wraps it into either a `PyramidalDataset` (extends `DefaultDataset`, for ImageJ) or a `PyramidalBdv` (per-channel BDV `SourceAndConverter` lists, volatile-wrapped per resolution level) – both implement the marker interface `Pyramidal`.

**Opening modes** (enum `ZarrOpenBehavior` in `ome.zarr.fijiui.open.options`):
- `IMAGEJ_HIGHEST_RESOLUTION` / `IMAGEJ_CUSTOM_RESOLUTION` → `ZarrOpenActions.openIJWithImage()`
- `BDV_MULTI_RESOLUTION` → `ZarrOpenActions.openBDVWithImage()`
- `SHOW_SELECTION_DIALOG` → `DnDActionChooser` Swing dialog with icon buttons

**Settings** are persisted across Fiji sessions via SciJava `PrefService`, read/written through `ZarrOpeningSettings` (open-behavior, preferred width, reader backend) and surfaced via the `OpeningBehaviorSettings` command. `UserScriptSettings` currently only logs the chosen script path – it does not persist it.

**Active-window tracking:** `PyramidalService` (a SciJava service) tracks the most-recently-focused `Pyramidal` window (BDV or ImageJ) via AWT focus listening; `PyramidalPreprocessor` auto-fills any `Pyramidal`-typed command parameter with the currently active one.

**Key utility classes:**
- `ZarrUtils` – consolidated Zarr-detection utility; `isZarr(URI)` handles both local filesystem (looks for `.zarray` / `zarr.json`) and HTTP (HEAD-requests known metadata files); `isHttpAccessible` is package-private
- `ClipboardUtils` – reads the system clipboard (`readClipboard()`) and converts strings to URIs (`stringToUri(String, Consumer<String>)`); `readClipboardAsUri(Consumer<String>)` combines both
- `BdvUtils` – shows a `PyramidalBdv` in a BDV window, applies OMERO channel colors/display-ranges, wires reference-counting and window-close cleanup
- `Affine3DUtils` – checks whether an `AffineTransform3D`'s linear part is a pure axis-aligned scaling (no rotation/shear)
- `ScriptUtils` – opens Fiji script editor with a pre-populated scriptlet

Note: `BdvHandleService` is test/example-only now (`ome-zarr-fiji-ui/src/test/java/ome/zarr/examples/demo/`), not part of the shipped plugin.

## Modules

Multi-module reactor. The root `pom.xml` is the aggregator (`ome.zarr:ome-zarr-parent`, packaging `pom`) and inherits `pom-scijava`. Each module lives in its own directory `ome-zarr-<name>/` with its own `pom.xml` and carries its own SciJava provenance (required by the enforcer). Five published modules:

- **`ome-zarr-imglib2`** – package `ome.zarr.imglib2` (+`.metadata`, `.exceptions`); backend-agnostic core (`PyramidBackend`, `PyramidContents`, `ZarrUtils`, `Affine3DUtils`). No Fiji or backend dependency.
- **`ome-zarr-n5`** – `ome.zarr.n5` (`N5PyramidBackend`); depends on imglib2 + external N5-universe (codecs `n5-zarr`/`n5-blosc`/zstd arrive transitively via `n5-universe`).
- **`ome-zarr-zarrjava`** – `ome.zarr.zarrjava` (`ZarrJavaPyramidBackend`); depends on imglib2 + `dev.zarr:zarr-java`.
- **`ome-zarr-fiji`** (+`.open`, `.plugins`, `.util`) – ImageJ/BDV integration (`ZarrOpener`, `PyramidalDataset`, `PyramidalBdv`, `PyramidalService`, `BdvUtils`). Depends on imglib2 only (no backend artifact); uses the *external* N5 library directly for the single-scale fallback in `ZarrOpener`.
- **`ome-zarr-fiji-ui`** – `ome.zarr.fijiui` (+`.open`, `.open.options`, `.plugin`, `.settings`, `.dialog`, `.util`); DnD handler, SciJava commands, dialogs, opening-behavior settings. Depends on all four other modules – the batteries-included artifact.

Dependency graph: `n5`, `zarrjava`, `fiji` each → `imglib2`; `fiji-ui` → {`imglib2`, `n5`, `zarrjava`, `fiji`}. Backends are selected at runtime (`ZarrReaderBackend`), so `fiji` needs at least one backend on the classpath at runtime even though it doesn't depend on one.

A sixth, non-published module **`ome-zarr-coverage-report`** only runs `jacoco:report-aggregate` to produce a cross-module coverage report for SonarCloud; it joins the reactor solely under the `coverage` profile.

**Shared test code and resources** live once under `test-shared/` at the repo root, wired into every module by `build-helper-maven-plugin` (`add-test-source`) and `<testResources>` in the parent pom:
- `test-shared/java/` – `ZarrTestUtils` (at the `ome.zarr` root) and `PyramidBackendTestBase` (at `ome.zarr.imglib2`), the shared backend test base subclassed by the n5/zarrjava/fiji suites.
- `test-shared/resources/` – sample OME-Zarr datasets (`ome/zarr/testdata/…`) and `logback-test.xml`.

`ZarrTestUtils.resourcePath()` resolves resources to a real filesystem `Path`, so they are copied into each module's `target/test-classes` via the shared `<testResources>` (a test-jar would expose them only as `jar:` URLs, which `Paths.get` rejects) – hence the shared-source approach rather than a published test-jar.