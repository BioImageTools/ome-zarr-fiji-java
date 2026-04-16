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
    * A github action is installed, which copies the release to maven.scijava.org
    * For reference: Release script https://github.com/scijava/scijava-scripts/blob/main/release-version.sh
    * [ ] Clone https://github.com/scijava/scijava-scripts repo
    * [ ] Ensure that one of the git remotes has the name "origin"
    * [ ] Close IntelliJ, if open
    * [ ] Run sh /path/to/release-version.sh from the project root directory
    * [ ] Confirm version number
    * [ ] The release script pushes to master on github.com
        * This triggers a *github Action* which copies the version to be released to maven central.
          cf. https://repo1.maven.org/maven2/sc/fiji/ome-zarr-fiji/
* [ ] Download created jar file from maven central (https://repo1.maven.org/maven2/sc/fiji/ome-zarr-fiji/)
    * [ ] Delete jar-file from last release version from local Fiji installation path (if any)
    * [ ] Copy jar file of the new version to local Fiji installation path
    * [ ] Copy new dependencies, if any
        * [ ]
    * [ ] Test, if Fiji starts successfully
    * [ ] Test new functionalities of to-be-released version in Fiji
* [ ] Copy Jar-File to [OME-Zarr-PREVIEW](https://sites.imagej.net/OME-Zarr-PREVIEW/) Update-Site using Fiji/ImageJ Updaters (Fiji > Help > Update...)
    * [ ] Set Updater to Advanced Mode
        * [ ] If needed, add `webdav:stefanhahmann` (or any other username for update site) as `Host` under
          `Manage Update Sites > OME-Zarr-PREVIEW`
    * [ ] Upload ome-zarr-fiji-*.*.*.jar
    * [ ] Check Upload success: https://sites.imagej.net/OME-Zarr-PREVIEW/jars/
* [ ] Add Release documentation: https://github.com/mastodon-sc/OME-Zarr-PREVIEW/releases
