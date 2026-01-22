package sc.fiji.ome.zarr.ui;

import bdv.util.BdvFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5Viewer;
import org.janelia.saalfeldlab.n5.bdv.N5ViewerCreator;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.scijava.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.ome.zarr.ui.util.CreateIcon;
import sc.fiji.ome.zarr.util.BdvHandleService;
import sc.fiji.ome.zarr.util.ScriptUtils;
import sc.fiji.ome.zarr.util.ZarrOnFileSystemUtils;

import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.AWTError;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DnDActionChooser
{

	private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String HELP_URL = "https://github.com/BioImageTools/ome-zarr-fiji-java/";

	private final Frame parentFrame;

	private final Path droppedInPath;

	private final Context context;

	@Nullable
	private final BdvHandleService bdvHandleService;

	private final JButton zarrToIJDialog;

	private final JButton zarrToBDVDialog;

	private final JButton zarrIJHighestResolution;

	private final JButton zarrBDVHighestResolution;

	private final JButton zarrScript;

	private final JButton help;

	private boolean extendedVersion;

	public DnDActionChooser( final Frame parentFrame, final Path path, final Context context,
			@Nullable final BdvHandleService bdvHandleService )
	{
		this.parentFrame = parentFrame;
		this.droppedInPath = path;
		this.context = context;
		this.bdvHandleService = bdvHandleService;

		this.extendedVersion = true;

		ImageIcon zarrIJIcon = CreateIcon.getAndResizeIcon( "zarr_ij_icon.png" );
		zarrToIJDialog = new JButton( zarrIJIcon );
		ImageIcon zarrBDVIcon = CreateIcon.getAndResizeIcon( "zarr_bdv_icon.png" );
		zarrToBDVDialog = new JButton( zarrBDVIcon );
		ImageIcon ijIcon = CreateIcon.getAndResizeIcon( "ij_icon.png" );
		zarrIJHighestResolution = new JButton( ijIcon );
		ImageIcon bdvIcon = CreateIcon.getAndResizeIcon( "bdv_icon.png" );
		zarrBDVHighestResolution = new JButton( bdvIcon );
		ImageIcon scriptIcon = CreateIcon.getAndResizeIcon( "script_icon.png" );
		zarrScript = new JButton( scriptIcon );
		ImageIcon helpIcon = CreateIcon.getAndResizeIcon( "help_icon.png" );
		help = new JButton( helpIcon );
	}

	public void setShowExtendedVersion( boolean show )
	{
		this.extendedVersion = show;
		logger.debug( "Show extended version: {}", show );
	}

	public void show()
	{
		final Point mouseLocation = getMouseLocation();
		if ( mouseLocation == null )
			return;

		final JDialog dialog = createDialog();
		final JPanel panel = initLayout();
		initBehaviour( dialog );

		dialog.getContentPane().add( panel );
		dialog.pack();
		positionDialog( dialog, mouseLocation );

		dialog.setVisible( true );
		dialog.requestFocus();
	}

	/** Creates the layout and adds the pre-initialized buttons. */
	private JPanel initLayout()
	{
		JPanel panel;
		if ( extendedVersion )
		{
			panel = new JPanel( new GridLayout( 3, 2, 5, 5 ) );
			panel.add( zarrToIJDialog );
			panel.add( zarrToBDVDialog );
			panel.add( zarrIJHighestResolution );
			panel.add( zarrBDVHighestResolution );
			panel.add( zarrScript );
			panel.add( help );
		}
		else
		{
			panel = new JPanel( new FlowLayout( FlowLayout.CENTER, 5, 5 ) );
			panel.add( zarrToIJDialog );
			panel.add( zarrToBDVDialog );
		}
		return panel;
	}

	/** Adds listeners and global behaviour (keyboard, fade, etc.). */
	private void initBehaviour( final JDialog dialog )
	{

		// zarr to FIJI importer button
		zarrToIJDialog.addActionListener( e -> {
			openN5ImporterDialog();
			dialog.dispose();
		} );
		zarrToIJDialog.setToolTipText( "Open Zarr/N5 Importer dialog" );

		// zarr to BDV viewer button
		zarrToBDVDialog.addActionListener( e -> {
			openN5ViewerDialog();
			dialog.dispose();
		} );
		zarrToBDVDialog.setToolTipText( "Open Zarr/N5 BDV Viewer dialog" );

		// FIJI button
		zarrIJHighestResolution.addActionListener( e -> {
			openIJAtSpecificResolutionLevel();
			dialog.dispose();
		} );
		zarrIJHighestResolution.setToolTipText( "Open Zarr/N5 in ImageJ at highest resolution level" );

		// BDV button
		zarrBDVHighestResolution.addActionListener( e -> {
			openBDVAtSpecificResolutionLevel();
			dialog.dispose();
		} );
		zarrBDVHighestResolution.setToolTipText( "Open Zarr/N5 in BDV at highest resolution level" );

		// script button
		String scriptName = ScriptUtils.getTooltipText( context );
		zarrScript.setToolTipText( "Open Zarr/N5 Script:\n\n" + scriptName );
		zarrScript.addActionListener( e -> {
			runScript();
			dialog.dispose();
		} );
		// help button
		help.addActionListener( e -> dialog.dispose() );
		help.setToolTipText( "Help about Zarr/N5 actions" );
		help.addActionListener( e -> {
			try
			{
				Desktop.getDesktop().browse( new URI( HELP_URL ) );
			}
			catch ( Exception ex )
			{
				logger.warn( "Cannot open help link: {}", ex.getMessage() );
			}
		} );

		setupCloseOnKeyboard( dialog );
		setupCloseOnMouseLeave( dialog );
	}

	private Point getMouseLocation()
	{
		try
		{
			return MouseInfo.getPointerInfo().getLocation();
		}
		catch ( AWTError e )
		{
			logger.warn( "Cannot get mouse pointer info", e );
			return null;
		}
	}

	private JDialog createDialog()
	{
		JDialog dialog = new JDialog( parentFrame );
		dialog.setUndecorated( true );
		dialog.setModal( false );
		dialog.setAlwaysOnTop( true );
		dialog.setOpacity( 1.0f );
		return dialog;
	}


	private static class ZarrEntry {
		Path rootFolderAsPath;
		String rootFolderAsString;
		List<String> openedSubFolders;

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder( "ZarrEntry details:\n" );
			sb.append( "  root path: "+rootFolderAsString+"\n" );
			openedSubFolders.forEach(p -> sb.append( "  unfolding over: "+p+"\n" ));
			return sb.toString();
		}
	}

	private ZarrEntry inspectDroppedPath()
	{
		final ZarrEntry ze = new ZarrEntry();
		ze.rootFolderAsPath = ZarrOnFileSystemUtils.findRootFolder( droppedInPath );
		ze.rootFolderAsString = ZarrOnFileSystemUtils.getUriFromPath( ze.rootFolderAsPath ).toString();
		ze.openedSubFolders = ZarrOnFileSystemUtils.listPathDifferences( droppedInPath, ze.rootFolderAsPath );
		return ze;
	}


	private void openN5ImporterDialog()
	{
		ZarrEntry ze = inspectDroppedPath();
		ze.openedSubFolders.add( 0, "" ); //NB: this seems to be helpful for the N5 dialog
		logger.info("{}", ze); //hmm, this makes SonarQube happy
		//
		new N5Importer().runWithDialog( ze.rootFolderAsString, ze.openedSubFolders );
		logger.info( "Opening zarr importer dialog at {}", ze.rootFolderAsString );
	}

	private void openN5ViewerDialog()
	{
		ZarrEntry ze = inspectDroppedPath();
		ze.openedSubFolders.add( 0, "" ); //NB: this seems to be helpful for the N5 dialog
		logger.info("{}", ze);
		//
		new N5ViewerCreator().runWithDialog( ze.rootFolderAsString, ze.openedSubFolders );
		logger.info( "Opening zarr viewer dialog at {}", ze.rootFolderAsString );
	}

	private void openBDVAtTopFolderLevel(ZarrEntry ze)
	{
		logger.info("TOP LEVEL BDV: starting with {}", ze.rootFolderAsString);
		N5Reader reader = new N5Factory().openReader( ze.rootFolderAsString );
		String[] datasets = reader.deepListDatasets("");
		if (datasets.length == 0) return;

		Set<String> grossDatasets = new HashSet<>(20);
		Arrays.stream(datasets).forEach( ds -> grossDatasets.add( ds.split("/")[0] ) );

		Object previousBDV = null;
		ze.openedSubFolders.add( 0, "" );
		for (String ds : grossDatasets) {
			ze.openedSubFolders.set( 0, ds );
			previousBDV = openBDVAtDatasetLevel(ze, previousBDV);
		}
	}

	private Object openBDVAtDatasetLevel(ZarrEntry ze)
	{
		return openBDVAtDatasetLevel(ze, null);
	}

	private Object openBDVAtDatasetLevel(ZarrEntry ze, Object previousBDV)
	{
		logger.info("{}", ze);
		logger.info("DATASET BDV: multisources from {} (starting new? {})", ze.openedSubFolders.get(0), previousBDV == null);

		//N5Reader reader = new N5Factory().openReader( ze.rootFolderAsString );
		//N5Viewer.show( reader, ze.openedSubFolders.get(0) ); //NB: the length of openedSubFolders is expected to be 1
		N5Viewer.show( ze.rootFolderAsString, ze.openedSubFolders.get(0) );
/*
		this is not parsing the metadata well
		String uri = droppedInPath.toAbsolutePath().toString();
		logger.info("DATASET BDV: full path: {}", uri);
		N5Viewer.show( uri );
*/
		return new int[0];
	}

	private void openBDVAtSubDatasetLevel(ZarrEntry ze)
	{
		String datasetPath = String.join( "/", ze.openedSubFolders );
		logger.info("DATASET BDV: sublevel, individual image at {}", datasetPath);

		String uri = droppedInPath.toAbsolutePath().toString();
		logger.info("DATASET BDV: full path: {}", uri);
		N5Viewer.show( uri );

/*
		N5Reader reader = new N5Factory().openReader( ze.rootFolderAsString );
		Img<?> lazyView = N5Utils.open(reader, datasetPath);
		if ( bdvHandleService == null )
			BdvFunctions.show( lazyView, datasetPath );
		else
			bdvHandleService.openNewBdv( lazyView, datasetPath );
*/
	}

	private void openBDVAtSpecificResolutionLevel() //TODO: rename
	{
		ZarrEntry ze = inspectDroppedPath();
		switch (ze.openedSubFolders.size()) {
			case 0:
				openBDVAtTopFolderLevel(ze);
				break;
			case 1:
				openBDVAtDatasetLevel(ze);
				break;
			default:
				openBDVAtSubDatasetLevel(ze);
		}
/*
		N5Reader reader = new N5Factory().openReader( ze.rootFolderAsString );
		String datasetPath = String.join( "/", ze.openedSubFolders );
		logger.info( "Opening a sub-path: {}", datasetPath);
		Img<?> lazyView = N5Utils.open(reader, datasetPath);
*/
/*
		final Path zarrDatasetPath = ZarrOnFileSystemUtils.findImageRootFolder( droppedInPath );
		if (zarrDatasetPath == null) {
			logger.error("TODO: List all datasets and open them as individual sources.");
			logger.error("TODO: This can be done iteratively e.g. via the BdvHandleService...");
			return;
		}
		final String zarrDatasetPathAsStr = ZarrOnFileSystemUtils.getUriFromPath( zarrDatasetPath ).toString();
		N5Reader reader = new N5Factory().openReader( zarrDatasetPathAsStr );
		String dataset = ZarrOnFileSystemUtils.findHighestResolutionByName( reader.deepListDatasets( "" ) );
*/
/*
		if ( bdvHandleService == null )
			BdvFunctions.show( lazyView, datasetPath );
		else
			bdvHandleService.openNewBdv( lazyView, datasetPath );
		logger.info( "open big data viewer with zarr at {}///{}", ze.rootFolderAsString, datasetPath );
*/
	}

	private void openIJAtSpecificResolutionLevel()
	{
		ZarrEntry ze = inspectDroppedPath();
		final Path zarrDatasetPath = ZarrOnFileSystemUtils.findImageRootFolder( droppedInPath );
		if (zarrDatasetPath == null) {
			errorReportAmbiguousZarrPath();
			return;
		}
		final String zarrDatasetPathAsStr = ZarrOnFileSystemUtils.getUriFromPath( zarrDatasetPath ).toString();
		N5Reader reader = new N5Factory().openReader( zarrDatasetPathAsStr );
		String dataset = ZarrOnFileSystemUtils.findHighestResolutionByName( reader.deepListDatasets( "" ) );
		ImageJFunctions.show( ( RandomAccessibleInterval ) N5Utils.open( reader, dataset ), dataset );
		logger.info( "open imageJ with zarr at {}", zarrDatasetPathAsStr );
	}

	private void runScript()
	{
		final Path zarrDatasetPath = ZarrOnFileSystemUtils.findImageRootFolder( droppedInPath );
		if (zarrDatasetPath == null) {
			errorReportAmbiguousZarrPath();
			return;
		}
		final String zarrDatasetPathAsStr = ZarrOnFileSystemUtils.getUriFromPath( zarrDatasetPath ).toString();
		logger.info( "run script with zarr root path at {}", zarrDatasetPathAsStr );
		ScriptUtils.executePresetScript( context, zarrDatasetPathAsStr );
	}

	private void errorReportAmbiguousZarrPath() {
		logger.error("Was given a top-level folder of a multi-dataset zarr, don't know which single dataset to open.");
		logger.error("Bailing out. Next time, please, drag-in a particular dataset, which is any sub-folder of the");
		logger.error("now dropped path ({}).", droppedInPath);
	}

	private void positionDialog( JDialog dialog, Point mouseLocation )
	{
		Dimension size = dialog.getSize();
		int x = mouseLocation.x - size.width / 2;
		int y = mouseLocation.y - size.height / 2;
		dialog.setLocation( x, y );
	}

	private void setupCloseOnKeyboard( final JDialog dialog )
	{
		dialog.getRootPane().registerKeyboardAction(
				e -> dialog.dispose(),
				KeyStroke.getKeyStroke( "ESCAPE" ),
				JComponent.WHEN_IN_FOCUSED_WINDOW
		);
	}

	private void setupCloseOnMouseLeave( final JDialog dialog )
	{
		final Timer checkMouse = new Timer( 200, e -> {
			PointerInfo pi = MouseInfo.getPointerInfo();
			if ( pi == null )
				return;
			Point p = pi.getLocation();
			if ( !dialog.getBounds().contains( p ) )
			{
				( ( Timer ) e.getSource() ).stop();
				startFadeOut( dialog );
			}
		} );
		checkMouse.start();

		dialog.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosed( WindowEvent e )
			{
				checkMouse.stop();
			}
		} );
	}

	private void startFadeOut( final JDialog dialog )
	{
		final float[] opacity = new float[] { 1.0f };
		final int steps = 10;
		final int duration = 300;
		final int interval = duration / steps;

		final Timer fade = new Timer( interval, e -> {
			opacity[ 0 ] -= 1.0f / steps;
			if ( opacity[ 0 ] <= 0f )
			{
				( ( Timer ) e.getSource() ).stop();
				dialog.dispose();
			}
			else
			{
				try
				{
					dialog.setOpacity( opacity[ 0 ] );
				}
				catch ( UnsupportedOperationException ex )
				{
					dialog.dispose();
					( ( Timer ) e.getSource() ).stop();
				}
			}
		} );
		fade.start();
	}
}
