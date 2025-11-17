package sc.fiji.ome.zarr.ui;

import bdv.util.BdvFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5ViewerCreator;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.scijava.Context;
import org.scijava.prefs.PrefService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.ome.zarr.plugins.PresetScriptPlugin;
import sc.fiji.ome.zarr.util.BdvHandleService;
import sc.fiji.ome.zarr.ui.util.CreateIcon;
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

public class DnDActionChooser {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

    public DnDActionChooser(final Frame parentFrame, final Path path, final Context context, @Nullable final BdvHandleService bdvHandleService) {
        this.parentFrame = parentFrame;
        this.droppedInPath = path;
        this.context = context;
        this.bdvHandleService = bdvHandleService;

        this.extendedVersion = true;

        ImageIcon zarrIJIcon = CreateIcon.getAndResizeIcon("zarr_ij_icon.png");
        zarrToIJDialog = new JButton(zarrIJIcon);
        ImageIcon zarrBDVIcon = CreateIcon.getAndResizeIcon("zarr_bdv_icon.png");
        zarrToBDVDialog = new JButton(zarrBDVIcon);
        ImageIcon ijIcon = CreateIcon.getAndResizeIcon("ij_icon.png");
        zarrIJHighestResolution = new JButton(ijIcon);
        ImageIcon bdvIcon = CreateIcon.getAndResizeIcon("bdv_icon.png");
        zarrBDVHighestResolution = new JButton(bdvIcon);
        ImageIcon scriptIcon = CreateIcon.getAndResizeIcon("script_icon.png");
        zarrScript = new JButton(scriptIcon);
        ImageIcon helpIcon = CreateIcon.getAndResizeIcon("help_icon.png");
        help = new JButton(helpIcon);
    }

    public void setShowExtendedVersion(boolean show) {
        this.extendedVersion = show;
        logger.debug("Show extended version: {}", show);
    }

    public void show() {
        final Point mouseLocation = getMouseLocation();
        if (mouseLocation == null) return;

        final JDialog dialog = createDialog();
        final JPanel panel = initLayout();
        initBehaviour(dialog);

        dialog.getContentPane().add(panel);
        dialog.pack();
        positionDialog(dialog, mouseLocation);

        dialog.setVisible(true);
        dialog.requestFocus();
    }

    /** Creates the layout and adds the pre-initialized buttons. */
    private JPanel initLayout() {
        JPanel panel;
        if (extendedVersion) {
            panel = new JPanel(new GridLayout(3, 2, 5, 5));
            panel.add(zarrToIJDialog);
            panel.add(zarrToBDVDialog);
            panel.add(zarrIJHighestResolution);
            panel.add(zarrBDVHighestResolution);
            panel.add(zarrScript);
            panel.add(help);
        } else {
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
            panel.add(zarrToIJDialog);
            panel.add(zarrToBDVDialog);
        }
        return panel;
    }


    /** Adds listeners and global behaviour (keyboard, fade, etc.). */
    private void initBehaviour(final JDialog dialog) {

        // zarr to FIJI importer button
        zarrToIJDialog.addActionListener(e -> {
            openN5ImporterDialog();
            dialog.dispose();
        });
        zarrToIJDialog.setToolTipText("Open Zarr/N5 Importer dialog");

        // zarr to BDV viewer button
        zarrToBDVDialog.addActionListener(e -> {
            openN5ViewerDialog();
            dialog.dispose();
        });
        zarrToBDVDialog.setToolTipText("Open Zarr/N5 BDV Viewer dialog");

        // FIJI button
        zarrIJHighestResolution.addActionListener(e -> {
            openIJAtSpecificResolutionLevel();
            dialog.dispose();
        });
        zarrIJHighestResolution.setToolTipText("Open Zarr/N5 in ImageJ at highest resolution level");

        // BDV button
        zarrBDVHighestResolution.addActionListener(e -> {
            openBDVAtSpecificResolutionLevel();
            dialog.dispose();
        });
        zarrBDVHighestResolution.setToolTipText("Open Zarr/N5 in BDV at highest resolution level");

        // script button
        String scriptName = getScriptName(context);
        zarrScript.setToolTipText("Open Zarr/N5 Script:\n\n" + scriptName);
        zarrScript.addActionListener(e -> {
            runScript();
            dialog.dispose();
        });
        // help button
        help.addActionListener(e -> dialog.dispose());
        help.setToolTipText("Help about Zarr/N5 actions");
        help.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(HELP_URL));
            } catch (Exception ex) {
                logger.warn("Cannot open help link: {}", ex.getMessage());
            }
        });


        setupCloseOnKeyboard(dialog);
        setupCloseOnMouseLeave(dialog);
    }

    private Point getMouseLocation() {
        try {
            return MouseInfo.getPointerInfo().getLocation();
        } catch (AWTError e) {
            logger.warn("Cannot get mouse pointer info", e);
            return null;
        }
    }

    private JDialog createDialog() {
        JDialog dialog = new JDialog(parentFrame);
        dialog.setUndecorated(true);
        dialog.setModal(false);
        dialog.setAlwaysOnTop(true);
        dialog.setOpacity(1.0f);
        return dialog;
    }

    private void openN5ImporterDialog() {
        final URI zarrRootPathAsURI = ZarrOnFileSystemUtils.getZarrRootPath(droppedInPath);
        final Path zarrRootPath = ZarrOnFileSystemUtils.findRootFolder(droppedInPath);
        new N5Importer().runWithDialog(zarrRootPathAsURI.toString(),
                ZarrOnFileSystemUtils.listPathDifferences(droppedInPath, zarrRootPath));
        logger.info("opened zarr importer dialog at {}", zarrRootPathAsURI);
    }

    private void openN5ViewerDialog() {
        final URI zarrRootPathAsURI = ZarrOnFileSystemUtils.getZarrRootPath(droppedInPath);
        final Path zarrRootPath = ZarrOnFileSystemUtils.findRootFolder(droppedInPath);
        new N5ViewerCreator().runWithDialog(zarrRootPathAsURI.toString(),
                ZarrOnFileSystemUtils.listPathDifferences(droppedInPath, zarrRootPath));
        logger.info("opened zarr viewer dialog at {}", zarrRootPathAsURI);
    }

    private void openBDVAtSpecificResolutionLevel() {
        final String zarrRootPathAsStr = ZarrOnFileSystemUtils.getZarrRootPath(droppedInPath).toString();
        N5Reader reader = new N5Factory().openReader(zarrRootPathAsStr);
        String dataset = ZarrOnFileSystemUtils.findHighestResolutionByName(reader.deepListDatasets(""));
        if (bdvHandleService == null) {
            BdvFunctions.show((Img<?>) N5Utils.open(reader, dataset), dataset);
        }
        else {
            this.bdvHandleService.openNewBdv(N5Utils.open(reader, dataset), dataset);
        }
        logger.info("open big data viewer with zarr at {}", zarrRootPathAsStr);
    }

    private void openIJAtSpecificResolutionLevel() {
        final String zarrRootPathAsStr = ZarrOnFileSystemUtils.getZarrRootPath(droppedInPath).toString();
        N5Reader reader = new N5Factory().openReader(zarrRootPathAsStr);
        String dataset = ZarrOnFileSystemUtils.findHighestResolutionByName(reader.deepListDatasets(""));
        ImageJFunctions.show((RandomAccessibleInterval) N5Utils.open(reader, dataset), dataset);
        logger.info("open imageJ with zarr at {}", zarrRootPathAsStr);
    }

    private void runScript()
    {
        final String zarrRootPathAsStr = ZarrOnFileSystemUtils.getZarrRootPath(droppedInPath).toString();
        logger.info("run script with zarr root path at {}", zarrRootPathAsStr);
        ScriptUtils.executePresetScript(context, zarrRootPathAsStr);
    }

    private void positionDialog(JDialog dialog, Point mouseLocation) {
        Dimension size = dialog.getSize();
        int x = mouseLocation.x - size.width / 2;
        int y = mouseLocation.y - size.height / 2;
        dialog.setLocation(x, y);
    }

    private void setupCloseOnKeyboard(final JDialog dialog) {
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void setupCloseOnMouseLeave(final JDialog dialog) {
        final Timer checkMouse = new Timer(200, e -> {
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi == null) return;
            Point p = pi.getLocation();
            if (!dialog.getBounds().contains(p)) {
                ((Timer) e.getSource()).stop();
                startFadeOut(dialog);
            }
        });
        checkMouse.start();

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                checkMouse.stop();
            }
        });
    }

    private void startFadeOut(final JDialog dialog) {
        final float[] opacity = new float[]{1.0f};
        final int steps = 10;
        final int duration = 300;
        final int interval = duration / steps;

        final Timer fade = new Timer(interval, e -> {
            opacity[0] -= 1.0f / steps;
            if (opacity[0] <= 0f) {
                ((Timer) e.getSource()).stop();
                dialog.dispose();
            } else {
                try {
                    dialog.setOpacity(opacity[0]);
                } catch (UnsupportedOperationException ex) {
                    dialog.dispose();
                    ((Timer) e.getSource()).stop();
                }
            }
        });
        fade.start();
    }

    private static final String DEFAULT_SCRIPT_NAME = "My Script";

    /**
     * @return the name of the script to run from preferences, or a default name if not set.
     */
    private String getScriptName(final Context context) {
        if (context == null) {
            return DEFAULT_SCRIPT_NAME;
        }
        PrefService prefService = context.getService(PrefService.class);
        if (prefService == null) {
            return DEFAULT_SCRIPT_NAME;
        }

        return prefService.get(PresetScriptPlugin.class, "scriptName", DEFAULT_SCRIPT_NAME);
    }
}
