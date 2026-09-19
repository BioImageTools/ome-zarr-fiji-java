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

### The goal: Progressive writing

This document outlines how progressive writing could be implemented. The source for the writing is `PyramidContents` — a holder of lazily-loaded pixel data at decreasing spatial resolutions, with additional metadata such as transforms, axis information, and display settings.

*Progressive writing* means here that

- **Writing** is **realized over multiple discrete API calls**.

- Steps may be separated by **arbitrary delays** and can even be executed by different processes. Order matters.

- Steps are not atomic and do not lock the storage. Each step, barring errors, leaves the store in a valid (though partially filled) OME-Zarr state.

- Writing is therefore **stateful**: method call order is significant.

### Requirements : Design decisions

Additional design decisions:

- A **single unified API** writes `PyramidContents`, implemented through *writer objects*.

- *Writer objects* hold **no pixel data** and buffer nothing beyond low-level I/O. All pixel data stays with the caller; writers write immediately.

- The writer API is **blocking**; no `flush()` is needed.

### Requirements : Writer objects

The *writer objects* in detail:

- Persistent-storage writers target a local or remote OME-Zarr store identified by URI/URL.

- For temporary (non-persistent) use, an in-memory writer is provided, keeping the API uniform.
  
  - This writer is backed by a `PyramidContents` it **can expose** — without exposure, the written pixels would be inaccessible and the writer pointless.
    
  - Persistent writers, by contrast, do **not** expose `PyramidContents` as they retain no pixel data.
  
- There is a subtle difference in how `PyramidContents` retains written pixel values depending on its origin:
  
  - A reader-backed `PyramidContents` uses `CachedCellImg`: evicted chunks revert to their original content. This is correct — a reader is not expected to persist modifications.

    - As a useful side effect, reader-backed `PyramidContents` still permits in-place modification — handy for memory-efficient processing, though the caller must account for the eviction behaviour.

  - `PyramidContents` passed to a writer need not contain pixel data. Writer setup reads only shape/geometry metadata.

  - Writer-backed `PyramidContents` uses `DiskCachedCellImg`: evicted chunks are swapped to disk and restored on re-access, so written data is never lost — correct behaviour for a write target.

### Remarks : Background developers' discussions

Based on discussions with @normanrz, @tpietzsch, and @stefanhahmann; see also the [Zulip channel](https://imagesc.zulipchat.com/#narrow/channel/626210-.5B2026-09.5D-OME-Zarr-Java-Hackathon/topic/.22unanchored.22.20PyramidContents.20in.20Fiji/with/623140734) and the whiteboard below.

![Progressive writing mockup whiteboard](https://www.fi.muni.cz/~xulman/files/progresive_writing_mockup_whiteboard.jpg)

### Remarks : Philosophy of the new proposed classes

Although it is natural to pass an existing `PyramidContents` to a writer, writers consult **only its shape/geometry** during construction — pixel arrays are never accessed and need not be present. `PyramidContents` is treated (only) as a compact description of the target structure (and nothing beyond this is consumed from `PyramidContents`). Because it does not cover storage-level details (chunk sizes, compression), `OmeZarrWritingOptions` fills that gap.

The `PyramidSaver` interface unifies the writer API around three explicit steps:

- create top-level OME-Zarr scaffold
  - metadata only, no pixels
  - not yet covered: HCS, labels, bf2raw specs for multi-image datasets
- create group to hold one OME-Zarr multiscales entry
  - metadata only, no pixels
  - if the HCS path to a multiscale is known, this method can be called directly
- write a region (typically called repeatedly)

Two persistent-storage writers and one in-memory writer are proposed. The persistent writers can be obtained via the existing `PyramidBackend` interface, letting the caller select the backing I/O library through the existing backend mechanism.

```java
// this is already existing in the codebase
public class PyramidBackend
{
    // this is already existing in the codebase
    PyramidContents read( URL );

    // the new stuff:
    //
    // create an object that is backed by the store/container at URL
    PyramidSaver createWriter( URL );
}
```

### OmeZarrWritingOptions

Analog to `BdvOptions`. The list of writing parameters is likely incomplete.

```java
public class OmeZarrWritingOptions
{
    // analogy to BdvOptions in how it is used;
    // the purpose is that PyramidContents prescribe only the shape/geometry
    // of the images and their pyramid levels, it doesn't tell storage-focused
    // technical details such as chunks sizes, shards sizes, compression method
    private shardSizePerLevel[]
    private chunkSizePerLevel[]
    private compressionPerLevel[]
    // TODO: check what everything OME-Zarr specs permits to set

    // intentionally no option to choose single-file OME-Zarr
    // as this one is hard to create __progressively__, which
    // is the main theme of this package

    // getters (return copies, make this obj truly immutable)
    .......

    // chaining-allowing setters
    OmeZarrWritingOptions setShardSize(...);
    OmeZarrWritingOptions setChunkSize(...);
    .......


    public void OmeZarrWritingOptions( PyramidContents pc )
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
    // When an implementing object is constructed, it is probably done with
    // a URL parameter (if that makes sense for the implementing class); that
    // said, implementing object is created for a particular URL, and another
    // one for another URL...
    // much like we are currently doing it with ZarrReaders backends


    // Write a skeleton OME-Zarr, only all metadata, no pixel data at all.
    // (There's anyway no pixel data provided to the PyramidSaver at this moment.)
    // (initContainer(URL) in the "whiteboard picture")
    void initEmptyContainer() throws IOException, "AlreadyOccupiedException";
    // NB: No URL provided! See above.
    // TODO Is this really a part of the _Pyramid_Saver?
    //      ...it cares only about the top-level OME-Zarr metadata

    // Since Vlado thinks it is NOT possible to recover/configure PyramidSaver from
    // a partially written OME-Zarr, the following interface methods are commented out.
    //
    /* void initFromExistingContainer( URL ) throws IOException; */
    /* void initFromExistingMultiscales( String path ) throws IOException; */


    // write a skeleton group for OME-Zarr 'multiscales', only all metadata, no pixel data yet
    // this assures that the below writeRegion() can work; in another words,
    // if this one method is skipped, the below writeRegion() will fail
    // (setupPyramid() in the "whiteboard picture")
    void initEmptyMultiscales( String path, PyramidContents pc, OmeZarrWritingOptions opts ) throws IOException, "AlreadyOccupiedException";

    void initEmptyMultiscales( String path, PyramidContents pc ) throws IOException, "AlreadyOccupiedException"
    {
        //default implementation:
        initEmptyMultiscales( path, pc, OmeZarrWritingOptions.defaultOptionsFor(pc) );
    }

    // The implementing class learns the data shape, pyramids and writing options
    // (chunks, compression) only via initEmptyMultiscales(); in fact, this is the
    // only way to change the OmeZarrWritingOptions et al.; therefore, these parameters
    // are firmly fixed during consecutive writeRegion() (so, e.g., it cannot happen
    // that some chunks are written with compression A and others with B).

    // write RAI, a region, imglib2's Interval with __proper__ min,max!
    // (no zero-based Interval that represents only size/diagonal
    //  with no "offset" of the interval shall be used,
    //  except for the single one, truly zero-based one)
    // (writeRegion() in the "whiteboard picture")
    void writeRegion( RAI, level ) throws IOException;
    // TODO assume RAI is only spatial coords? add parameters for time point and channel?

    // NB: all methods write immediately; there's
    //     no explicit intermediate memory and no flush();
    //     the memory with pixels is thus handled (and allocated)
    //     solely by the caller

    // NB: the PyramidSaver __does not__ provide any downsampling routine;
    //     caller knows the best
    //     a) how to handle lower-res versions of the data,
    //     b) when to write lower-res versions of the data.
    //
    // For the "how": There shall be prepared Util functions for the usual
    // downsampling that will heavily utilize the imglib's Views.resample().
    //
    // For the "when": It is desired to implement this in another layer above,
    // which would took care of work planning, workers communication, etc.
    //
    // For the "how & when": I could imagine an util method to write
    // the whole time point (of a particular channel) of raw/mask data
    // incl. all resolution levels, for example.
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

    // shortcut convenience method, outside the PyramidSaver interface, so that
    // the implementation can ask for implementation-specific additional parameters;
    // the method assumes PyramidContents __has valid pixel__ arrays and writes it fully
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

    // shortcut convenience method, outside the PyramidSaver interface, so that
    // the implementation can ask for implementation-specific additional parameters;
    // the method assumes PyramidContents __has valid pixel__ arrays and writes it fully
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
        // TODO Should it throw IllegalUse-alike exception?
        // Vlado votes to only silently log/info only, while doing nothing; switching
        // to this saver then requires no modifications in the caller's pipeline
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

    // Notice the absence of the convenience write() method !
    // (which is another reason why the write() is not part of the interface PyramidSaver)

    // this is the store into which RAIs are written
    private final PyramidContents data;

    @Override
    public PyramidContents getPyramidContents()
    {
        return this.data;
    }
    // NB: This can be used to wrap into PyramidalDataset or PyramidalBdv
    // NB: Direct writing is also possible, avoiding this.writeRegion()
}
```

### Implementations : Helper class to aid PyramidSaver.writeRegion()

```java
public static class PyramidSaverUtils
{
    // Example Util class, subject to discussion and changes !! 
    // only method signatures are listed below

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
