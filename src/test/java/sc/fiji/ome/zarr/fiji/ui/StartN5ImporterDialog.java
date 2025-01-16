package sc.fiji.ome.zarr.fiji.ui;

import org.janelia.saalfeldlab.n5.ij.N5Importer;
import sc.fiji.ome.zarr.fiji.ui.util.ZarrOnFSutils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class StartN5ImporterDialog {
	public static void main(String[] args) {
		final Path droppedPath = Paths.get("/temp/Zurich.hackathon.testData/maybe_top_level.zarr/CTC_trainTrif02_TP35/");
		final Path zarrRootPath = ZarrOnFSutils.findRootFolder(droppedPath);

		N5Importer importer = new N5Importer();
		importer.runWithDialog(zarrRootPath.toAbsolutePath().toString(), ZarrOnFSutils.listPathDifferences(droppedPath,zarrRootPath));
	}
}
