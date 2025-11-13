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

public class ContextMenuAroundMouse {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Frame parentFrame;

    private final Path droppedInPath;

    private final ImageIcon iconZarr = CreateIcon.getAndResizeIcon("zarr_icon.png");
    private final ImageIcon iconBDV = CreateIcon.getAndResizeIcon("bdv_icon.png");
    private final ImageIcon iconScript = CreateIcon.getAndResizeIcon("script_icon.png");
    private final ImageIcon iconBDVAdd = CreateIcon.getAndResizeIcon("bdv_add_icon.png");

    private boolean extendedVersion;

    public ContextMenuAroundMouse(final Frame parentFrame, final Path path) {
        this.parentFrame = parentFrame;
        this.droppedInPath = path;
        this.extendedVersion = true;
    }

    public void setShowExtendedVersion(boolean show) {
        this.extendedVersion = show;
        logger.debug("Large submenu: {}", show);
    }

    public void showSubmenu() {
        final Point mouseLocation = getMouseLocation();
        if (mouseLocation == null) return;

        final JDialog dialog = createDialog();
        final JPanel panel = createPanel(dialog);
        dialog.getContentPane().add(panel);
        dialog.pack();
        positionDialog(dialog, mouseLocation);

        setupKeyboardClose(dialog);
        setupMouseLeaveClose(dialog);

        dialog.setVisible(true);
        dialog.requestFocus();
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

    private JPanel createPanel(JDialog dialog) {
        JPanel panel;
        if (extendedVersion) {
            panel = new JPanel(new GridLayout(2,2,5,5));
            JButton button1 = new JButton(iconZarr);
            JButton button2 = new JButton(iconBDV);
            JButton button3 = new JButton(iconScript);
            JButton button4 = new JButton(iconBDVAdd);
            button1.addActionListener(e -> openDialog());
            button2.addActionListener(e -> openBDV());
            button2.addActionListener(e -> dialog.dispose());
            panel.add(button1);
            panel.add(button2);
            panel.add(button3);
            panel.add(button4);
        } else {
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
            JButton button1 = new JButton(iconZarr);
            JButton button2 = new JButton(iconBDV);
            button2.addActionListener(e -> dialog.dispose());
            panel.add(button1); panel.add(button2);
        }
        return panel;
    }

    private String getZarrRootPath (final Path path)
    {
        if (path != null) {
            final Path zarrRootPath = ZarrOnFSutils.findRootFolder(path);
            final String zarrRootPathAsStr = (ZarrOnFSutils.isWindows() ? "/" : "")
                    + zarrRootPath.toAbsolutePath().toString().replace("\\\\","/");
            logger.info("zarrRootPath: {}", zarrRootPathAsStr);
            return zarrRootPathAsStr;
        }
        return null;
    }

    private void openDialog()
    {
        final String zarrRootPathAsStr = getZarrRootPath(droppedInPath);
        final Path zarrRootPath = ZarrOnFSutils.findRootFolder(droppedInPath);
        new N5Importer().runWithDialog(zarrRootPathAsStr,
                ZarrOnFSutils.listPathDifferences(droppedInPath, zarrRootPath));
        logger.info("opened zarr at {}", zarrRootPathAsStr);
    }

    private void openBDV()
    {
        final String zarrRootPathAsStr = getZarrRootPath(droppedInPath);
        N5Reader reader = new N5Factory().openReader(zarrRootPathAsStr);
        String dataset = ZarrOnFSutils.findHighestResByName( reader.deepListDatasets("") );
        BdvFunctions.show((Img<?>) N5Utils.open(reader, dataset), dataset);
        logger.info("opened zarr at {}", zarrRootPathAsStr);
    }

    private void positionDialog(JDialog dialog, Point mouseLocation) {
        Dimension size = dialog.getSize();
        int x = mouseLocation.x - size.width / 2;
        int y = mouseLocation.y - size.height / 2;
        dialog.setLocation(x, y);
    }

    private void setupKeyboardClose(JDialog dialog) {
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void setupMouseLeaveClose(JDialog dialog) {
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
        final float[] opacity = {1.0f};
        final int steps = 10;
        final int duration = 300;
        final int interval = duration / steps;

        Timer fade = new Timer(interval, e -> {
            opacity[0] -= 1.0f / steps;
            if (opacity[0] <= 0f) {
                ((Timer) e.getSource()).stop();
                dialog.dispose();
            } else {
                try { dialog.setOpacity(opacity[0]); }
                catch (UnsupportedOperationException ex) { dialog.dispose(); ((Timer) e.getSource()).stop(); }
            }
        });
        fade.start();
    }
}
