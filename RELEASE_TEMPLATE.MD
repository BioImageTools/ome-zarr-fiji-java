# Release

* [ ] Merge Open Pull Requests
* [ ] Documentation
    * [ ] Update Documentation in the repo
    * [ ] Screenshots with demo data (Optional)
    * [ ] Screencasts (Optional)
    * [ ] Point to example data (e.g. celltrackingchallenge, Kos data from Elephant paper)
* [ ] Update license information
    * [ ] Run "mvn license:update-file-header" and commit
* [ ] Update release date and version in CITATION.cff file and commit
* [ ] Run release Script (de-snapshots, sets a tag and sets the next snapshot version, generates javadoc, runs unit
  tests)
    * A github action is installed, which copies the release artifacts to maven.scijava.org
    * For reference: Release script https://github.com/scijava/scijava-scripts/blob/main/release-version.sh
    * [ ] Clone https://github.com/scijava/scijava-scripts repo
    * [ ] Ensure that one of the git remotes has the name "origin"
    * [ ] Close IntelliJ, if open
    * [ ] Run sh /path/to/release-version.sh from the project root directory
    * [ ] Confirm version number
    * [ ] The release script pushes to master on github.com
        * This triggers a *github Action* which copies the version to be released to maven.scijava.org.
        * https://maven.scijava.org/repository/releases/ome/zarr/ome-zarr-fiji/
        * https://maven.scijava.org/repository/releases/ome/zarr/ome-zarr-fiji-ui/
        * https://maven.scijava.org/repository/releases/ome/zarr/ome-zarr-imglib2/
        * https://maven.scijava.org/repository/releases/ome/zarr/ome-zarr-n5/
        * https://maven.scijava.org/repository/releases/ome/zarr/ome-zarr-zarrjava/
        * Snapshots of the main branch land in the snapshots repo instead, e.g.
          https://maven.scijava.org/repository/snapshots/ome/zarr/ome-zarr-fiji/
* [ ] Download the created artifacts from
  maven.scijava.org
    * The build releases five artifacts: [
      `ome-zarr-imglib2`](https://maven.scijava.org/repository/releases/ome/zarr/ome-zarr-imglib2/), [
      `ome-zarr-n5`](https://maven.scijava.org/repository/releases/ome/zarr/ome-zarr-n5/), [
      `ome-zarr-zarrjava`](https://maven.scijava.org/repository/releases/ome/zarr/ome-zarr-zarrjava/),
      [`ome-zarr-fiji`](https://maven.scijava.org/repository/releases/ome/zarr/ome-zarr-fiji/), [
      `ome-zarr-fiji-ui`](https://maven.scijava.org/repository/releases/ome/zarr/ome-zarr-fiji-ui/) (plus the
      `ome-zarr-java` POM).
    * [ ] Delete the jar-files from the last release version from the local Fiji installation path (if any)
    * [ ] Copy the five new module jars to the local Fiji installation path
    * [ ] Copy new dependencies, if any
        * [ ] zarr-java-0.1.3.jar
    * [ ] Test, if Fiji starts successfully
    * [ ] Test new functionalities of to-be-released version in Fiji
* [ ] Copy Jar-File to [OME-Zarr-PREVIEW](https://sites.imagej.net/OME-Zarr-PREVIEW/) Update-Site using Fiji/ImageJ
  Updaters (Fiji > Help > Update...)
    * [ ] Set Updater to Advanced Mode
        * [ ] If needed, add `webdav:stefanhahmann` (or any other valid username for the update site) as `Host` under
          `Manage Update Sites > OME-Zarr-PREVIEW`
    * [ ] Upload all five module jars: ome-zarr-imglib2-*.jar, ome-zarr-n5-*.jar, ome-zarr-zarrjava-*.jar,
      ome-zarr-fiji-*.jar, ome-zarr-fiji-ui-*.jar
    * [ ] Check Upload success: https://sites.imagej.net/OME-Zarr-PREVIEW/jars/
* [ ] Add Release documentation: https://github.com/BioImageTools/ome-zarr-fiji-java/releases
* [ ] Make an announcement on image.sc: https://forum.image.sc/t/ngff-weekly-dev-update-thread/110810/
