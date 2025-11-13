package sc.fiji.ome.zarr.ui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.invoke.MethodHandles;

public class ContextMenuAroundMouse {

    private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final Frame parentFrame;

	private final ImageIcon iconBDV = CreateIcon.getAndResizeIcon("bdv_icon.png");
	private final ImageIcon iconZarr = CreateIcon.getAndResizeIcon("zarr_icon.png");
    private final ImageIcon iconBDVAdd = CreateIcon.getAndResizeIcon("bdv_add_icon.png");

	private boolean shouldShowCustomItems = false;

	public void toggleCustomItems() {
		shouldShowCustomItems = !shouldShowCustomItems;
        logger.debug("Large submenu: {} ", shouldShowCustomItems);
	}

	public ContextMenuAroundMouse(Frame parentFrame) {
		this.parentFrame = parentFrame;
        this.toggleCustomItems();
	}

    public void showSubmenu() {
        final Point mouseLocation = MouseInfo.getPointerInfo().getLocation();

        final JDialog submenuDialog = new JDialog(parentFrame);
        submenuDialog.setUndecorated(true);
        submenuDialog.setModal(false);
        submenuDialog.setAlwaysOnTop(true);
        submenuDialog.setOpacity(1.0f); // full visible initially

		// Create panel with the buttons, two layouts supported
		JPanel panel;

		if (shouldShowCustomItems) {
			// 2x2 grid layout
			panel = new JPanel(new GridLayout(2, 2, 5, 5));

			JButton button1 = new JButton(iconZarr);
			JButton button2 = new JButton(iconBDV);
			JButton button3 = new JButton("Option 3");
			JButton button4 = new JButton(iconBDVAdd);

			button2.addActionListener(e -> {
                logger.debug("closing submenu");
                submenuDialog.dispose();
			});

			panel.add(button1);
			panel.add(button2);
			panel.add(button3);
			panel.add(button4);
		} else {
			// 2x1 horizontal layout
			panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));

			JButton button1 = new JButton(iconZarr);
			JButton button2 = new JButton(iconBDV);

			button2.addActionListener(e -> {
                logger.debug("closing submenu");
                submenuDialog.dispose();
			});

			panel.add(button1);
			panel.add(button2);
		}

        submenuDialog.getContentPane().add(panel);
        submenuDialog.pack();

		// Position at mouse cursor
		final Dimension windowSize = submenuDialog.getSize();
		int centeredX = mouseLocation.x - (windowSize.width / 2);
		int centeredY = mouseLocation.y - (windowSize.height / 2);
        submenuDialog.setLocation(centeredX, centeredY);

        // --- Mouse leave detection using Timer ---
        final Timer closeTimer = new Timer(200, e -> {
            PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            if (pointerInfo == null) return;
            Point mouse = pointerInfo.getLocation();
            Rectangle bounds = submenuDialog.getBounds();
            if (!bounds.contains(mouse)) {
                ((Timer) e.getSource()).stop();
                startFadeOut(submenuDialog);
            }
        });

        closeTimer.start();

        submenuDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                closeTimer.stop();
            }
        });

		submenuDialog.setVisible(true);
        submenuDialog.requestFocus();
    }

    /**
     * Smoothly fades out the given dialog before disposing it.
     */
    private void startFadeOut(final JDialog dialog) {
        logger.debug("Starting fade-out animation");

        final float[] opacity = {1.0f};
        final int fadeSteps = 10;          // total steps
        final int fadeDuration = 300;      // ms total duration
        final int interval = fadeDuration / fadeSteps; // ms per step

        Timer fadeTimer = new Timer(interval, e -> {
            opacity[0] -= 1.0f / fadeSteps;
            if (opacity[0] <= 0f) {
                ((Timer) e.getSource()).stop();
                dialog.dispose();
            } else {
                try {
                    dialog.setOpacity(opacity[0]);
                } catch (UnsupportedOperationException ex) {
                    // setOpacity not supported on all systems
                    dialog.dispose();
                    ((Timer) e.getSource()).stop();
                }
            }
        });
        fadeTimer.start();
    }
}
