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

This issue aims to outline/frame how progressive writing could be implemented. The subject of the writing for now shall be the `PyramidContents`, a holder of lazily-loaded pixel data (images) at decreasing spatial resolutions (pyramids), with a couple of additional metadata such as transforms, axes information, or display settings.

*Progressive writing* means here that

- **Writing** of final OME-Zarr is **realized over multiple smaller steps**, API calls.

- There can be **arbitrary time delays in between the steps**. They can even be realized by different processes. Nevertheless, their order is important.

- The steps are not meant to be atomic, to temporarily lock the output storage. Yet, the steps are expected (provided they don't break during their operation) to always leave the storage with a valid OME-Zarr, albeit partially filled.

- Writing is thus **not state-less**. Writing methods cannot be called/executed in any arbitrary order.

### Requirements : Design decisions

A few more deliberate design decisions:

- There shall be just **one, unified API for writing** `PyramidContents`, implemented by means of *writer objects*.

- *Writer objects* shall not keep any pixel data, shall not cache/buffer anything beyond the usual I/O low-level business. The `imglib2` **pixel data shall be only with the caller**, not also (in full or in part) at the writer. Consequently, writer objects should write immediately.

- *Writer objects* **API** should be **blocking**. There's no need for `flush()` in their API.

### Requirements : Writer objects

Description of the *writer objects*: 

- Writer objects for persistent local or remote storage are expected to write into an OME-Zarr format. They point to their storage space with a URI/URL.

- For temporary (not persistent), yet progressively constructed `PyramidContents`, a writer object that "writes only to RAM" shall be used. That way, the same *writers API* is always used.
  
  - Notice the wording: This writer is filling and is backed by the `PyramidContents`, which it **can expose** (otherwise this writer would be of any use if one wouldn't be able to consume the written pixels).
    
  - Notice, and in contrast, writers for persistent storage shall not expose `PyramidContents` because they shall not keep any pixel data themselves.
  
- There's a subtle detail in the behavior of `PyramidContents` when keeping pixel values written to their underlying pixel arrays.
  
  - `PyramidContents` obtained from backend readers is backed by `CachedCellImg` that allows writing. When the cells (chunks) are evicted from RAM, their rewritten new content is lost (behavior of the `CachedCellImg`), re-visiting the cell (chunk) brings back the original data. This is correct behavior because a *reader* is not expected to modify its source data, and thus the `PyramidContents` is expected to expose the source data.

    - It is, however, a nice design "flaw" that *reader*'s `PyramidContents` still permits changing its content. That's perfect for in-place (memory saving) image processing. Only the caller must be careful...

  - `PyramidContents` provided to the writers need not hold any pixel data; see below. Setting up the writers cares only about metadata such as image arrays' shape/geometry.

  - `PyramidContents` exposed from the *in-memory writers* are backed by `DiskCachedCellImg` that allows writing. But when the cells (chunks) are evicted from RAM, their content is stored, and retrieved when the cell is accessed again; basically a swapping mechanism comes with the `DiskCachedCellImg`. It is correct that they are not losing written data, as this is precisely their job (again, the `PyramidContents` came from a *writer object* and serves here as target storage).

### Remarks : Background developers' discussions

The following is based on kind (spoken) discussions with @normanrz , @tpietzsch , and @stefanhahmann . Some materials were created during that, namely the [Zulip channel](https://imagesc.zulipchat.com/#narrow/channel/626210-.5B2026-09.5D-OME-Zarr-Java-Hackathon/topic/.22unanchored.22.20PyramidContents.20in.20Fiji/with/623140734) and the following *whiteboard picture*.

![Progressive writing mockup whiteboard](https://www.fi.muni.cz/~xulman/files/progresive_writing_mockup_whiteboard.jpg)

### Remarks : Philosophy of the new proposed classes

It is natural to associate any *existing* `PyramidContents` object with the writer objects, and aim to write it. In the end, this is easily possible in the proposed code below. However, writer objects during their construction consider from `PyramidContents` **only its shape/geometry** of the multi-resolution pyramidal data; pixel arrays are never considered for the writer objects construction, and thus may be unavailable. `PyramidContents` is considered as a convenient compact holder of some defining parameters of the future OME-Zarr. Nevertheless, `PyramidContents` doesn't hold everything needed to write OME-Zarr, and that justifies the proposed `OmeZarrWritingOptions` class; see below.

In an attempt to unify the *writer objects* API, an interface `PyramidSaver` has been introduced that explicitly mentions the steps:

- create top-level OME-Zarr scaffold
  - only metadata, no pixels
  - missing here: start writing HCS, labels, bf2raw specs for multi-images
- create group to hold one piece of OME-Zarr multiscales
  - only metadata, no pixels
  - if HCS path to a multiscale is somehow known, this can interface method can be used
- write (most of the time: repeatedly!) region

Finally, two persistent-storage writer objects are proposed, as well as one in-memory writer. The former two could be instantiated from the already existing `PyramidBackend` interface, which enables the caller to choose a backing I/O library using the already existing codebase.

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

...as an analog to `BdvOptions`. The list of OME-Zarr writing parameters is currently (probably) incomplete.

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

... the main API for the progressive writing.

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

... that could probably be realized commonly in an abstract class; especially when, for example, the ZarrJava API could be matched to that of N5.

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
