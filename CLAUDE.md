# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

A Fiji/ImageJ plugin that provides drag-and-drop support for opening OME-Zarr (Open Microscopy Environment – Zarr) image datasets. It supports OME-Zarr v0.4 (Zarr v2) and v0.5 (Zarr v3) and integrates with both ImageJ and BigDataViewer for multi-resolution visualization.

## Build and test commands

```bash
mvn clean package          # build
mvn test                   # run all tests (requires 2 GB heap – configured in pom.xml)
mvn test -Dtest=ClassName  # run a single test class
mvn test -Dtest=ClassName#methodName   # run a single test method
mvn clean test -Pcoverage  # build with JaCoCo coverage report
```

Blosc native library is required for tests. On macOS:
```bash
brew install c-blosc
export DYLD_LIBRARY_PATH=$(brew --prefix c-blosc)/lib:$DYLD_LIBRARY_PATH
export JAVA_TOOL_OPTIONS=-Djava.library.path=$(brew --prefix c-blosc)/lib
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

Note: `BdvHandleService` is test/example-only now (`src/test/java/ome/zarr/examples/demo/`), not part of the shipped plugin.

**Package root:** `ome.zarr`, organized per future artifact:
- `ome.zarr.imglib2` (+`.metadata`, `.exceptions`) – backend-agnostic core (`PyramidBackend`, `PyramidContents`)
- `ome.zarr.n5` / `ome.zarr.zarrjava` – N5 / zarr-java backend implementations
- `ome.zarr.fiji` (+`.open`, `.plugins`, `.util`) – Fiji/BDV integration layer, no backend dependency
- `ome.zarr.fijiui` (+`.open`, `.open.options`, `.plugin`, `.settings`, `.dialog`, `.util`) – Fiji UI layer (sibling of `ome.zarr.fiji`, future `ome-zarr-fiji-ui` artifact): DnD handler, SciJava commands, dialogs, opening-behavior settings
- `ZarrUtils` lives in `ome.zarr.imglib2` (no Fiji/backend dependency); `ZarrTestUtils` sits directly at the `ome.zarr` root

**Test resources** (sample OME-Zarr datasets) live under `src/test/resources/` and are accessed via `ZarrTestUtils.resourcePath()`.