package sc.fiji.ome.zarr.ui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class DnDActionChooserDemo {

	private static void setupFrame(JFrame frame, DnDActionChooser menu) {
		frame.setTitle("Keyboard Submenu Example");
		frame.setSize(600, 400);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationRelativeTo(null);

		// Add a label for visual feedback
		JLabel label = new JLabel("Press 'K' to show submenu at cursor position", JLabel.CENTER);
		frame.add(label);
		frame.setVisible(true);

		// Use key bindings instead of KeyListener for better reliability
		JRootPane rootPane = frame.getRootPane();

		rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_K, 0), "showSubmenu");

		rootPane.getActionMap().put("showSubmenu", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				menu.show();
			}
		});

		rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, 0), "changeSubmenu");

		//rootPane.getActionMap().put("changeSubmenu", (e) -> shouldShowCustomItems ^= true );
		rootPane.getActionMap().put("changeSubmenu", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				menu.setShowExtendedVersion(false);
			}
		} );
	}


	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			final JFrame mainFrame = new JFrame();
			final DnDActionChooser menu = new DnDActionChooser(mainFrame, null, null, null);
			setupFrame(mainFrame, menu);
		});
	}
}
