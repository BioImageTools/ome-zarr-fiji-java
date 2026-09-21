### Claude's executive summary

**Goal:** Write `PyramidContents` to OME-Zarr in multiple steps, with arbitrary delays between steps, always leaving valid (partially filled) OME-Zarr in storage.

**Key design decisions:**
- One unified API via *writer objects* (`PyramidSaver` interface)
- Writers hold no pixel data, write immediately, blocking API (no `flush()`)
- In-memory writer uses `DiskCachedCellImg` as backing store and exposes `PyramidContents`

**Proposed classes:**

| Class | Purpose |
|---|---|
| `OmeZarrWritingOptions` | Chunk/shard/compression config, analog to `BdvOptions` |
| `PyramidSaver` (interface) | `initEmptyContainer()` → `initEmptyMultiscales()` → `writeRegion()` |
| `N5PyramidSaver` | Persistent storage via N5 |
| `ZarrJavaPyramidSaver` | Persistent storage via zarr-java |
| `InMemoryPyramidSaver` | RAM writer, also implements `Pyramidal` to expose contents |
| `PyramidSaverUtils` | Downsampling helpers (`writeRawImagePyramid`, `writeMaskImagePyramid`, etc.) |

---

The document was Claude-edited from [the original text by @xulman](https://gist.github.com/xulman/d84f8d7827197dc6d24cfa20704f3ae8).

---

### The goal: Progressive writing

This document outlines how progressive writing could be implemented. The source for the writing is `PyramidContents` — a holder of lazily-loaded pixel data at decreasing spatial resolutions, with additional metadata such as transforms, axis information, and display settings.

*Progressive writing* means here that

- **Writing** is **realized over multiple discrete API calls**.

- Steps may be separated by **arbitrary delays** and can even be executed by different processes. Order matters.

- Steps are not atomic and do not lock the storage. Each step, barring errors, leaves the store in a valid (though partially filled) OME-Zarr state.

- Writing is therefore **stateful**: method call order is significant.

### Requirements : Design decisions

Additional design decisions:

- A **single unified API** is writing `PyramidContents`, doesn't matter to where it is writing, implemented through *writer objects*.

- *Writer objects* hold **no pixel data** and buffer nothing beyond low-level I/O. All pixel data stays with the caller.

- Writers write immediately; the writer API is **blocking**; no `flush()` is needed.

### Requirements : Writer objects

The *writer objects* in detail:

- Persistent-storage writers target a local or remote OME-Zarr store identified by URI/URL.

- For temporary (non-persistent) use, an in-memory writer is provided, keeping the API uniform.
  
  - This writer is backed by a `PyramidContents` it **can expose** — without exposure, the written pixels would be inaccessible and the writer pointless.
    
  - Persistent writers, by contrast, do **not** expose `PyramidContents` as they retain no pixel data.
  
- <mark>ⓘ NOTE:</mark> There is a subtle difference in how `PyramidContents` retains written pixel values depending on its origin:
  
  - A <u>reader-backed</u> `PyramidContents` uses `CachedCellImg`: <u>evicted chunks revert</u> to their original content. This is correct — a reader is not expected to persist modifications.

    - As a useful side effect, reader-backed `PyramidContents` still permits in-place modification (can write to the `PyramidContents` own memory) — handy for memory-efficient processing, though the caller must account for the eviction behaviour.

  - `PyramidContents` passed to a <u>*writer object* need not contain pixel data</u>. Writer setup cares/reads only shape/geometry metadata and resolution levels.

  - <u>Writer-backed</u> `PyramidContents` uses `DiskCachedCellImg`: <u>evicted chunks are swapped</u> to disk and restored on re-access, so written data is never lost — correct behaviour for a write target.

### Remarks : Background developers' discussions

Based on discussions with @normanrz, @tpietzsch, and @stefanhahmann; see also the [Zulip channel](https://imagesc.zulipchat.com/#narrow/channel/626210-.5B2026-09.5D-OME-Zarr-Java-Hackathon/topic/.22unanchored.22.20PyramidContents.20in.20Fiji/with/623140734) and the whiteboard below.

![Progressive writing mockup whiteboard](https://www.fi.muni.cz/~xulman/files/progresive_writing_mockup_whiteboard.jpg)

### Remarks : The philosophy behind, motivation and recap

###### To start writing, pixels are not needed, but chunks params are.

Although it is natural to pass an existing `PyramidContents` to a writer, writers consult **only its shape/geometry** during construction — pixel arrays are never accessed and need not be present. `PyramidContents` is treated (only) as a compact description of the target structure (and nothing beyond this is consumed from `PyramidContents`). Because it does not cover storage-level details (chunk sizes, compression), `OmeZarrWritingOptions` fills that gap (but this is, for example, not required for the in-memory writer).

###### Writing API is not designed for all-in-one writing.

The `PyramidSaver` interface unifies the writer API around three explicit steps:

- create top-level OME-Zarr scaffold
  - metadata only, no pixels
  - not yet covered: HCS, labels, bf2raw specs for multi-image datasets
- create group to hold one OME-Zarr multiscales entry
  - metadata only, no pixels
  - if the HCS path to a multiscale is known, this method can be called directly
- write a region (typically called repeatedly)

###### Two persistent-storage writers and one in-memory writer are proposed.

The persistent writers can be obtained via the existing `PyramidBackend` interface, letting the caller select the backing I/O library through the existing backend mechanism.

```java
// this is already existing in the codebase
public class PyramidBackend
{
    // this is already existing in the codebase
    PyramidContents read( URL );

    // the new stuff:
    // create an object that is backed by the store/container at URL
    PyramidSaver createWriter( URL );
}
```

### Reference : Parameters required to write a multiscales OME-Zarr (Recapitulation)

Sources: [OME-Zarr dev spec](https://ngff.openmicroscopy.org/specifications/dev/index.html), [ngff-zarr docs](https://ngff-zarr.readthedocs.io/en/latest/).
Collected by Claude.

**OME-Zarr group / multiscales metadata**
- Spec version (`ome.version`, e.g. `"0.9.dev1"`)
- Multiscale name (`multiscales[].name`, optional)
- Downsampling method label (`multiscales[].type`, optional, e.g. `"gaussian"`)
- Downsampling method detail (`multiscales[].metadata` — version, args, kwargs; optional)

**Axes (coordinate system, one definition shared across all levels)**
- Number of axes (= array rank)
- Per axis: name (e.g. `"t"`, `"c"`, `"z"`, `"y"`, `"x"`), type (`"time"`, `"channel"`, `"space"`, `"array"`), unit (UDUNITS-2 string, e.g. `"micrometer"`, `"millisecond"`; omit for channel/array axes)

**Per-level entries (same structure repeated for each resolution level)**
- Relative path (`datasets[].path`, e.g. `"s0"`, `"s1"`, …); must be ordered highest → lowest resolution
- Scale transform: one float per axis encoding physical voxel size in axis units (`coordinateTransformations[].scale`)
- Translation transform: one float per axis for physical offset (`coordinateTransformations[].translation`; optional, zero by default)

**Array properties (uniform across all levels)**
- Array shape (`shape`): one integer per axis
- Data type (`data_type` / `dtype`): e.g. `uint8`, `float32`; labels must use integer types
- Fill value (`fill_value`): default for unwritten chunks; must be compatible with dtype
- Zarr format version (`zarr_format`): `2` or `3`
- Memory order (`order`): `"C"` (row-major) or `"F"` (column-major); Zarr v2 only
- Dimension names (`dimension_names`): optional labels per axis, should match axis names

**Chunking**
- Chunk shape (`chunks` in v2 / `chunk_grid.configuration.chunk_shape` in v3): one integer per axis
- Chunk key separator (`chunk_key_encoding`): `"/"` (v3 default) or `"."` (v2 style)

**Sharding (Zarr v3 only, optional layer above chunks)**
- Shard shape (coarse grid)
- Inner chunk shape (fine grid within each shard)
- Index location (`"start"` or `"end"`)
- Index codec pipeline (codecs used to compress the shard index itself)

**Codec pipeline (compression; applies per chunk or per inner chunk inside a shard)**
- Byte order / endianness (`"bytes"` codec: `"little"` or `"big"`)
- Compression codec (choose one):
  - GZip: `level` (1–9)
  - Zstd: `level` (negative = faster, higher = better ratio)
  - Blosc: codec name (`"lz4"`, `"zstd"`, `"zlib"`, …), compression level (0–9), shuffle mode (`"noshuffle"`, `"shuffle"`, `"bitshuffle"`), block size
  - CRC32C: checksum only, no compression

**Pyramid structure (drives the above; not stored as a standalone field)**
- Number of resolution levels (determines how many `datasets` entries there are)
- Downsampling factors per axis per level (drives the `scale` values at each level)
- Downsampling method: nearest-neighbour for labels/masks, interpolating for raw images

**Optional OMERO display metadata (per channel)**
- Color (6-digit hex RGB, e.g. `"FF0000"`), label string, visibility (`active` boolean)
- Display range: `window.start`, `window.end`, `window.min`, `window.max`
- Invert LUT (`inverted` boolean)

### OmeZarrWritingOptions

Analog to `BdvOptions`. Covers only what `PyramidContents` cannot supply.

```java
public class OmeZarrWritingOptions
{
    /*
     * Used analogously to BdvOptions. The following are already derivable from
     * PyramidContents and therefore absent here:
     *   - axis count, names, types, units  (from AxisCalibration)
     *   - array shape and data type        (from CachedCellImg + PyramidContents.type)
     *   - scale/translation transforms     (stored per level in PyramidContents)
     *   - number of resolution levels
     *   - OMERO display metadata           (optional field in PyramidContents)
     *   - key separator, zarr format etc.  (choices made by the package, thus hardcoded)
     */

    // --- Zarr format ---
    //zarrFormat and chunkKeySeparator will be hardcoded to v3 and '/'

    // --- Array storage ---
    private Object fillValue;             // default value for unwritten chunks; default: 0
    //memoryOrder will be hardcoded to "F"

    // --- Chunking (per level; auto-computed from array shape if not set) ---
    private int[][] chunkShapePerLevel;

    // --- Sharding (Zarr v3 only; null = no sharding) ---
    private int[][] shardShapePerLevel;       // coarse shard grid
    private int[][] innerChunkShapePerLevel;  // fine chunk grid inside each shard
    private String shardIndexLocation;        // "start" or "end"; default: "end"

    // --- Codec pipeline (per level; same codec applied to all levels if not set per level) ---
    // NB: in Zarr v3 byte order (endianness) is part of this pipeline ("bytes" codec)
    private Compression[] compressionPerLevel;

    // --- Optional OME-Zarr multiscales annotations ---
    private String multiscaleName;            // default: null
    private String downscalingType;           // e.g. "gaussian"; default: null
    private Object downscalingMetadata;       // method version, args, kwargs; default: null

    /*
     * Intentionally no option for single-file OME-Zarr: single-file archives
     * are hard to create progressively, which is the central theme of this package.
     */

    // getters (return copies, make this obj truly immutable)
    .......

    // chaining-allowing setters
    OmeZarrWritingOptions setFillValue( Object value );
    .......


    public OmeZarrWritingOptions( PyramidContents pc )
    {
        ......
        // NB: PyramidContents is a mandatory input to give the implementations
        // a chance to automagically setup (at least) the chunks sizes; they are,
        // however, not required to use that info (PyramidContents)
    }

    //alternative (and more verbose) way to construct an instance of this class
    public static OmeZarrWritingOptions defaultOptionsFor( PyramidContents pc )
    {
        return new OmeZarrWritingOptions( pc );
    }
}
```

### interface PyramidSaver

The main API for progressive writing.

```java
interface PyramidSaver
{
    /*
     * Implementing objects are constructed with a URL parameter (where applicable):
     * one instance per target URL, analogous to the existing backend reader:
     * ZarrJavaPyramidBackend.java#readPyramid(URI),
     * N5PyramidBackend.java#readPyramid(URI).
     */


    /*
     * Writes a skeleton OME-Zarr with most metadata but no pixel data.
     * (No pixel data is available to PyramidSaver at this point.)
     * (initContainer(URL) on the whiteboard)
     */
    void initEmptyContainer() throws IOException, "AlreadyOccupiedException";
    /*
     * NB: No URL provided — see above.
     * TODO: Is this really part of PyramidSaver? It only concerns
     *       the top-level OME-Zarr metadata.
     */

    /*
     * Recovering or reconfiguring a PyramidSaver from a partially written OME-Zarr
     * is not supported; the following interface methods are therefore commented out.
     */
    /* void initFromExistingContainer( URL ) throws IOException; */
    /* void initFromExistingMultiscales( String path ) throws IOException; */


    /*
     * Writes a skeleton OME-Zarr 'multiscales' group — metadata only, no pixel data.
     * writeRegion() requires this to be called first; skipping it will cause writeRegion() to fail.
     * (setupPyramid() on the whiteboard)
     */
    void initEmptyMultiscales( String path, PyramidContents pc, OmeZarrWritingOptions opts ) throws IOException, "AlreadyOccupiedException";

    void initEmptyMultiscales( String path, PyramidContents pc ) throws IOException, "AlreadyOccupiedException"
    {
        //default implementation:
        initEmptyMultiscales( path, pc, OmeZarrWritingOptions.defaultOptionsFor(pc) );
    }

    /*
     * Shape, pyramid layout, and writing options (chunks, compression) are communicated
     * solely through initEmptyMultiscales(); there is no other way to set them. These
     * parameters are therefore fixed for all subsequent writeRegion() calls — it cannot
     * happen that some chunks are written with compression A and others with B.
     */

    /*
     * Writes a region given as an imglib2 RAI with correct min/max coordinates.
     * A zero-based Interval (min == 0, encoding only the size) is valid only for
     * a single full-image write covering the entire array.
     * (writeRegion() on the whiteboard)
     */
    void writeRegion( RAI, level ) throws IOException;
    // TODO assume RAI is only spatial coords? add parameters for time point and channel?

    /*
     * All methods write immediately. There is no intermediate buffer and no flush().
     * Pixel memory is allocated and owned entirely by the caller.
     */

    /*
     * PyramidSaver provides no downsampling: the caller is responsible for both
     * how (which interpolation) and when to produce lower-resolution levels.
     *
     * For the "how": PyramidSaverUtils will provide utility functions for the
     * common case, using imglib2's Views.resample().
     *
     * For the "when": scheduling belongs in a layer above, handling work
     * planning and worker coordination.
     *
     * For the "how & when": a convenience method covering a full time point
     * (single channel, all resolution levels) is a plausible addition to PyramidSaverUtils.
     */
}
```

### Implementations : Persistent-storage writers

These could share an abstract base class, particularly if the ZarrJava API aligns closely enough with N5.

```java
public class N5PyramidSaver implements PyramidSaver
{
    public void N5PyramidSaver( URL )
    { .......... }

    @Override
    void initEmptyContainer()
    { .......... }

    @Override
    void initEmptyMultiscales( String path, PyramidContents pc, OmeZarrWritingOptions opts )
    { .......... }

    @Override
    void writeRegion( RAI, level );
    { .......... }

    /*
     * Convenience shortcut outside the PyramidSaver interface, allowing
     * implementation-specific parameters. Assumes PyramidContents contains
     * valid pixel arrays and writes all resolution levels in full.
     */
    public static PyramidSaver write( URL url, String path, PyramidContents pc, OmeZarrWritingOptions opts )
    {
        PyramidSaver saver = new N5PyramidSaver( url );
        saver.initEmptyContainer();
        saver.initEmptyMultiscales( path, pc, opts );
        for (int level = 0; level < pc.numResolutionLevels(); ++level)
        {
            saver.writeRegion( pc.asImg( level ), level );
        }
    }
}
```

```java
public class ZarrJavaPyramidSaver implements PyramidSaver
{
    public void ZarrJavaPyramidSaver( URL )
    { .......... }

    @Override
    void initEmptyContainer()
    { .......... }

    @Override
    void initEmptyMultiscales( String path, PyramidContents pc, OmeZarrWritingOptions opts )
    { .......... }

    @Override
    void writeRegion( RAI, level );
    { .......... }

    /*
     * Convenience shortcut outside the PyramidSaver interface, allowing
     * implementation-specific parameters. Assumes PyramidContents contains
     * valid pixel arrays and writes all resolution levels in full.
     */
    public static PyramidSaver write( URL url, String path, PyramidContents pc, OmeZarrWritingOptions opts )
    { .......... }
}
```

### Implementations : In-memory writer

```java
public class InMemoryPyramidSaver implements PyramidSaver, Pyramidal
{
    public void InMemoryPyramidSaver()
    {
        this( DiskCachedCellImgOptions.options() );
    }

    public void InMemoryPyramidSaver( DiskCachedCellImgOptions opts )
    {
        // ...to configure the underlying DiskCachedCellImg machinery
        this.opts = opts;
    }

    private DiskCachedCellImgOptions opts;


    @Override
    void initEmptyContainer()
    {
        /* empty */
        /*
         * TODO: Should this throw an IllegalStateException?
         * Current preference: silently log and do nothing, so switching to this
         * saver requires no changes in the caller's pipeline.
         */
    }

    @Override
    void initEmptyMultiscales( String path, PyramidContents pc, OmeZarrWritingOptions opts )
    {
          final var imgFactory = new DiskCachedCellImgFactory( pc.type );

          // iterate over pc.numResolutionLevels() and for each 'level':
          var img = imgFactory.create( pc.asImg(level).dimensions() );

          // to eventually set up a fresh new local data
          this.data = ....
    }

    @Override
    void writeRegion( RAI, level );
    {
        LoopBuilder over RAI and Views.Interval(this.data.asImg(level), RAI)...
    }

    /*
     * Note the absence of a convenience write() method — another reason why
     * write() is not part of the PyramidSaver interface.
     */

    // this is the store into which RAIs are written
    private final PyramidContents data;

    @Override
    public PyramidContents getPyramidContents()
    {
        return this.data;
    }
    /*
     * The returned PyramidContents can be wrapped into PyramidalDataset or PyramidalBdv.
     * Direct writing to its pixel arrays is also possible, bypassing writeRegion().
     */
}
```

### Implementations : Helper class to aid PyramidSaver.writeRegion()

```java
public static class PyramidSaverUtils
{
    /*
     * Example utility class — signatures only; subject to discussion and change.
     */

    public static void writeRawImagePyramid( RAI, timePoint, channel, PyramidSaver )
    public static void writeMaskImagePyramid( RAI, timePoint, channel, PyramidSaver )
    // NB: raw and mask differ in the downsampling method (interpolation vs. nearest-neighbor)

    public static RAI rawImageDownsampledView( RAI, float downScaleFactors[] )
    public static RAI maskImageDownsampledView( RAI, float downScaleFactors[] )

    public static float[] downScaleFactors( PyramidContents, fromLevel, toLevel )
    // NB: assert(fromLevel < toLevel)  (0 = highest res level)
}
```

### Example code snippets to satisfy several user stories

TODO Vlado
