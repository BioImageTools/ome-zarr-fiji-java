# Add zarr-java backend for Zarr v3 support

## Summary

This PR adds [zarr-java](https://github.com/zarr-developers/zarr-java) as an alternative backend for reading OME-Zarr multiscale images, enabling **Zarr v3** support alongside the existing N5-based backend.

The zarr-java backend is tried first when opening a multiscale image. If it cannot open the image (e.g. due to unsupported metadata), the existing N5 backend is used as a fallback.

## Changes

- **`ZarrJavaBackedPyramidal5DImageData`** — new `Pyramidal5DImageData` implementation backed by zarr-java. Supports OmeZarr v0.4, v0.5 and future versions v0.6 (RFC-5) and v1.0 (RFC-8 Collections), reads OME-NGFF multiscale metadata via `MultiscaleImage.open()`.
- **`ZarrJavaCellLoader`** — imglib2 `CellLoader` bridge: reads zarr chunks via `Array.read()`, handles C-order ↔ F-order axis reversal, uses `IndexIterator.getDoubleNext()` for correct unsigned type handling.
- **`ZarrOpenActions`** — updated `openMultiScaleImage()` to try zarr-java backend first and fall back to N5.
- **`pom.xml`** — added `dev.zarr:zarr-java:0.1.1-SNAPSHOT` dependency.

## Prerequisites: building zarr-java locally

The required zarr-java version is not yet released. You need to build and install it from the `ome-zarr` branch:

```bash
git clone https://github.com/zarr-developers/zarr-java.git
cd zarr-java
git checkout ome-zarr
# Bump version to distinguish from released 0.1.0 (already done on the branch if you use this fork)
mvn install -DskipTests -Dgpg.skip
```

This installs `dev.zarr:zarr-java:0.1.1-SNAPSHOT` into your local Maven repository (`~/.m2`), which is what this project's `pom.xml` depends on.

## Testing

New test class `ZarrJavaBackedPyramidal5DImageDataTest` mirrors the existing `DefaultPyramidal5DImageDataTest` and covers:

- `asPyramidalDataset()`, `asDataset()`, `asSources()`
- `numDimensions()`, `numTimepoints()`, `numChannels()`, `numResolutionLevels()`
- `voxelDimensions()`, `getName()`
- Pyramid level access and pixel value correctness (`testGetPyramidLevels`)

Run only the new backend tests and the open-actions integration tests:

```bash
mvn test -Dtest="ZarrJavaBackedPyramidal5DImageDataTest,ZarrOpenActionsTest"
```
