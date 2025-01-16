package sc.fiji.ome.zarr.fiji.ui;

import bdv.util.BdvFunctions;
import net.imglib2.img.Img;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.scijava.plugin.Attr;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.log.LogService;

import org.scijava.io.IOPlugin;
import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.location.FileLocation;
import org.scijava.io.location.Location;
import sc.fiji.ome.zarr.fiji.ui.util.ZarrOnFSutils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

@Plugin(type = IOPlugin.class, attrs = @Attr(name = "eager"))
public class ZarrDndHandlerPlugin extends AbstractIOPlugin<Object> implements Runnable {

	// ========================= logging stuff =========================
	@Parameter
	private LogService logService;

	//NB: Change _only_ here to the wanted log level: info() vs. debug()
	private final Reporter log = (msg) -> logService.info(this.getClass().getName()+" "+msg);
	private interface Reporter { void message(String msg); }

	// ========================= IOPlugin stuff =========================
	@Override
	public boolean supportsOpen(Location source) {
		final String sourcePath = source.getURI().getPath();
		log.message("was questioned: "+sourcePath);

		if (!(source instanceof FileLocation)) return false;
		if (!ZarrOnFSutils.isZarrFolder( Paths.get(source.getURI()) )) return false;
		return true;
	}

	@Override
	public Object open(Location source) throws IOException {
		log.message("was asked to open: "+source.getURI().getPath());
		final FileLocation fsource = source instanceof FileLocation ? (FileLocation)source : null;

		//debugging the DnD a bit.... but both tests should never fail
		if (fsource == null) return null;
		if (!ZarrOnFSutils.isZarrFolder(fsource.getFile().toPath())) return null;

		this.droppedInPath = fsource.getFile().toPath();
		//NB: shouldn't be null as fsource is already a valid OME Zarr path (see above)

		//not going to display anything now, we instead start a thread that delays itself a bit
		//and only opens after a waiting period; the waiting period is used to detect whether
		//the ALT key has been released (that is, if it had been pressed during the drag-and-drop operation)
		new Thread(this).start();
		return FAKE_INPUT;
	}

	//the "innocent" product of the (hypothetical) file reading... which Fiji will not display
	private static final Object FAKE_INPUT = new ArrayList<>(0);

	@Override
	public Class<Object> getDataType() {
		return Object.class;
	}

	// ========================= the actual opening of the dropped-in path =========================
	private Path droppedInPath = null;

	private void openRecentlyDroppedPath() {
		//do anything only when the argument is valid
		if (droppedInPath != null) {
			final Path zarrRootPath = ZarrOnFSutils.findRootFolder(droppedInPath);
			final String zarrRootPathAsStr = (isWindows() ? "/" : "")
					+ zarrRootPath.toAbsolutePath().toString().replaceAll("\\\\","/");
			log.message("is opening now: " + zarrRootPathAsStr);

			if (wasAltKeyDown) {
				N5Reader reader = new N5Factory().openReader(zarrRootPathAsStr);
				String dataset = ZarrOnFSutils.findHighestResByName( reader.deepListDatasets("") );
				BdvFunctions.show((Img<?>)N5Utils.open(reader, dataset), dataset);
			} else {
				new N5Importer().runWithDialog(zarrRootPathAsStr,
						ZarrOnFSutils.listPathDifferences(droppedInPath, zarrRootPath));
			}
			log.message("opened.");
		}

		//flag that this argument is processed
		droppedInPath = null;
	}

	public static boolean isWindows() {
		final String myOS = System.getProperty("os.name").toLowerCase();
		return !( myOS.contains( "mac" ) || myOS.contains( "nux" ) || myOS.contains( "nix" ) );
	}

	private static boolean wasAltKeyDown = false;
	private static final long PERIOD_FOR_DETECTING_ALT_KEY = 2000; //millis

	// ========================= stuff to detect if ALT was pressed during the drag-and-drop =========================
	// ------------------------- keyboard monitor -------------------------
	private static boolean isAlreadyRegisteredKeyHandler = false;

	public ZarrDndHandlerPlugin() {
		super();

		//install a keyboard events monitor, but only once!
		if (!isAlreadyRegisteredKeyHandler) {
			KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
				if (e.getKeyCode() == KeyEvent.VK_ALT) {
					//...monitor only if the ALT key did some action
					wasAltKeyDown = e.getID() == KeyEvent.KEY_RELEASED;
				}
				return false;
			});
			isAlreadyRegisteredKeyHandler = true;
		}
	}

	// ========================= stuff to detect if ALT was pressed during the drag-and-drop =========================
	// ------------------------- separate thread that fires GUI to keep application's focus to
	//                           allow its keyboard monitor to read-out anything while waiting a bit -------------------------
	@Override
	public void run() {
		wasAltKeyDown = false;
		//NB: this waiting period below is here only to give keyboard events
		//    a chance to notify us that the ALT key has been released
		openNotificationWindow();
		try { Thread.sleep(PERIOD_FOR_DETECTING_ALT_KEY); } catch (InterruptedException e) { /* empty */ }
		closeNotificationWindow();
		openRecentlyDroppedPath();
	}

	private JFrame notificationWindow = null;
	private void openNotificationWindow() {
		if (notificationWindow == null) {
			notificationWindow = new JFrame("Zarr Drag-and-Drop");
			notificationWindow.add(new JLabel("<html><br/><center><i>Opening...</i></center><br/>( <b>Alt+DnD</b> opens in <b>BigDataViewer</b> directly. )<br/></html>"));
			notificationWindow.pack();

			//window placement
			final Rectangle currentScreenSize = notificationWindow.getGraphicsConfiguration().getBounds();
			notificationWindow.setLocation(
					(int)currentScreenSize.getCenterX() - notificationWindow.getSize().width/2,
					(int)currentScreenSize.getCenterY() - notificationWindow.getSize().height/2
			);
			if (notificationWindow.isAlwaysOnTopSupported()) notificationWindow.setAlwaysOnTop(true);
		}
		notificationWindow.setVisible(true);
	}

	private void closeNotificationWindow() {
		notificationWindow.setVisible(false);
	}
}