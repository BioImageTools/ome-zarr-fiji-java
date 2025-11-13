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
	private JWindow submenuWindow;

	private final ImageIcon iconBDV = CreateIcon.fetchAndResizeIcon("https://avatars.githubusercontent.com/u/9824453?s=200&v=4");
	private final ImageIcon iconImageJ = CreateIcon.fetchAndResizeIcon("https://zarr-specs.readthedocs.io/en/latest/_static/logo.png");

	private boolean shouldShowCustomItems = false;

	public void toggleCustomItems() {
		shouldShowCustomItems = !shouldShowCustomItems;
        logger.debug("Large submenu: {} ", shouldShowCustomItems);
	}

	public ContextMenuAroundMouse(Frame parentFrame) {
		this.parentFrame = parentFrame;
	}

	public void showSubmenu() {
		// Close existing submenu if there is one,
		// as we want to rebuild because user settings may have changed
		if (submenuWindow != null && submenuWindow.isVisible()) {
			submenuWindow.dispose();
		}

		// Get current mouse position ASAP
		final Point mouseLocation = MouseInfo.getPointerInfo().getLocation();

		// Create undecorated window (no title bar, borders, etc.)
		submenuWindow = new JWindow(parentFrame);

		// Create panel with the buttons, two layouts supported
		JPanel panel;

		if (shouldShowCustomItems) {
			// 2x2 grid layout
			panel = new JPanel(new GridLayout(2, 2, 5, 5));

			JButton button1 = new JButton(iconImageJ);
			JButton button2 = new JButton(iconBDV);
			JButton button3 = new JButton("Option 3");
			JButton button4 = new JButton("Option 4");

			button2.addActionListener(e -> {
                logger.debug("closing submenu");
				submenuWindow.dispose();
			});

			panel.add(button1);
			panel.add(button2);
			panel.add(button3);
			panel.add(button4);
		} else {
			// 2x1 horizontal layout
			panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));

			JButton button1 = new JButton(iconImageJ);
			JButton button2 = new JButton(iconBDV);

			button2.addActionListener(e -> {
                logger.debug("closing submenu");
				submenuWindow.dispose();
			});

			panel.add(button1);
			panel.add(button2);
		}

		submenuWindow.add(panel);
		submenuWindow.pack();

		// Position at mouse cursor
		final Dimension windowSize = submenuWindow.getSize();
		int centeredX = mouseLocation.x - (windowSize.width / 2);
		int centeredY = mouseLocation.y - (windowSize.height / 2);
		submenuWindow.setLocation(centeredX, centeredY);

		// Make it close when focus is lost
		submenuWindow.addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowLostFocus(WindowEvent e) {
				submenuWindow.dispose();
			}
		});

		submenuWindow.setVisible(true);
	}
}
