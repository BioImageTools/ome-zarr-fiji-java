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

**Entry point:** `DnDHandlerPlugin` – a SciJava `IOPlugin` that intercepts drag-and-drop of filesystem paths, checks whether the path is a Zarr folder via `ZarrOnFileSystemUtils`, then delegates to `ZarrOpenActions`.

**Core data model:** `DefaultPyramidal5DImageData` (implements `Pyramidal5DImageData`) wraps an N5 reader and exposes the multi-resolution pyramid, channel/timepoint metadata, affine transforms, and conversion to BigDataViewer sources or ImageJ datasets.

**Opening modes** (enum `ZarrOpenBehavior`):
- `IMAGEJ_HIGHEST_RESOLUTION` / `IMAGEJ_CUSTOM_RESOLUTION` → `ZarrOpenActions.openIJWithImage()`
- `BDV_MULTI_RESOLUTION` → `ZarrOpenActions.openBDVWithImage()`
- `SHOW_SELECTION_DIALOG` → `DnDActionChooser` Swing dialog with icon buttons

**Settings** are persisted across Fiji sessions via SciJava `PrefService` in `ZarrDragAndDropOpenSettings`.

**Key utility classes:**
- `ZarrOnFileSystemUtils` – detects valid Zarr roots by looking for `.zarray` / `.zarr.json`
- `BdvHandleService` – SciJava service managing the BigDataViewer window lifecycle
- `BdvUtils` / `Affine3DUtils` – BigDataViewer and affine-transform helpers
- `ScriptUtils` – opens Fiji script editor with a pre-populated scriptlet

**Package root:** `sc.fiji.ome.zarr`

**Test resources** (sample OME-Zarr datasets) live under `src/test/resources/` and are accessed via `ZarrTestUtils.resourcePath()`.