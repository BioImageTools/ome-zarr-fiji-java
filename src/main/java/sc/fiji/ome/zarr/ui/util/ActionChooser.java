package sc.fiji.ome.zarr.ui.util;

import bdv.util.BdvFunctions;
import net.imglib2.img.Img;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.ij.N5Importer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.AWTError;
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
import java.nio.file.Path;

public class ActionChooser {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Frame parentFrame;
    private final Path droppedInPath;

    private final JButton buttonZarr;
    private final JButton buttonBDV;
    private final JButton buttonScript;
    private final JButton buttonBDVAdd;

    private boolean extendedVersion;

    public ActionChooser(final Frame parentFrame, final Path path) {
        this.parentFrame = parentFrame;
        this.droppedInPath = path;
        this.extendedVersion = true;

        ImageIcon iconZarr = CreateIcon.getAndResizeIcon("zarr_icon.png");
        buttonZarr = new JButton(iconZarr);
        ImageIcon iconBDV = CreateIcon.getAndResizeIcon("bdv_icon.png");
        buttonBDV = new JButton(iconBDV);
        ImageIcon iconScript = CreateIcon.getAndResizeIcon("script_icon.png");
        buttonScript = new JButton(iconScript);
        ImageIcon iconBDVAdd = CreateIcon.getAndResizeIcon("bdv_add_icon.png");
        buttonBDVAdd = new JButton(iconBDVAdd);
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
            panel = new JPanel(new GridLayout(2, 2, 5, 5));
            panel.add(buttonZarr);
            panel.add(buttonBDV);
            panel.add(buttonScript);
            panel.add(buttonBDVAdd);
        } else {
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
            panel.add(buttonZarr);
            panel.add(buttonBDV);
        }
        return panel;
    }


    /** Adds listeners and global behaviour (keyboard, fade, etc.). */
    private void initBehaviour(final JDialog dialog) {

        // Add action listeners
        buttonZarr.addActionListener(e -> openN5ImporterDialog());
        buttonBDV.addActionListener(e -> {
            openBDVAtSpecificResolutionLevel();
            dialog.dispose();
        });
        buttonScript.addActionListener(e -> dialog.dispose());
        buttonBDVAdd.addActionListener(e -> dialog.dispose());

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

    private void openBDVAtSpecificResolutionLevel() {
        final String zarrRootPathAsStr = ZarrOnFileSystemUtils.getZarrRootPath(droppedInPath);
        N5Reader reader = new N5Factory().openReader(zarrRootPathAsStr);
        String dataset = ZarrOnFileSystemUtils.findHighestResolutionByName(reader.deepListDatasets(""));
        BdvFunctions.show((Img<?>) N5Utils.open(reader, dataset), dataset);
        logger.info("opened big data viewer with zarr at {}", zarrRootPathAsStr);
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
}
