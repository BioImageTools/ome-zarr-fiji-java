package sc.fiji.ome.zarr.fiji.ui.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ContextMenuAroundMouse extends JFrame {
    private JWindow submenuWindow;
    private final ImageIcon iconBDV = new CreateIcon("https://avatars.githubusercontent.com/u/9824453?s=200&v=4").getIcon();
    private final ImageIcon iconImageJ = new CreateIcon("https://imagej.net/media/icons/imagej2.png").getIcon();

    private boolean shouldShowCustomItems = false;

    public ContextMenuAroundMouse() {
        setTitle("Keyboard Submenu Example");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Add a label for visual feedback
        JLabel label = new JLabel("Press 'K' to show submenu at cursor position", JLabel.CENTER);
        add(label);

        // Set up key binding for 'k' key
        setupKeyBinding();

        setVisible(true);
    }

    private void setupKeyBinding() {
        // Use key bindings instead of KeyListener for better reliability
        JRootPane rootPane = getRootPane();

        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_K, 0), "showSubmenu");

        rootPane.getActionMap().put("showSubmenu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSubmenu();
            }
        });

        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_L, 0), "changeSubmenu");

        //rootPane.getActionMap().put("changeSubmenu", (e) -> shouldShowCustomItems ^= true );
        rootPane.getActionMap().put("changeSubmenu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                shouldShowCustomItems = !shouldShowCustomItems;
                System.out.println("Large submenu: "+shouldShowCustomItems);
            }
        } );
    }

    private void showSubmenu() {
        // Close existing submenu if there is one,
        // as we want to rebuild because user settings may have changed
        if (submenuWindow != null && submenuWindow.isVisible()) {
            submenuWindow.dispose();
        }

        // Get current mouse position ASAP
        final Point mouseLocation = MouseInfo.getPointerInfo().getLocation();

        // Create undecorated window (no title bar, borders, etc.)
        submenuWindow = new JWindow(this);

        // Create panel with the buttons, two layouts supported
        JPanel panel;

        if (shouldShowCustomItems) {
            // 2x2 grid layout
            panel = new JPanel(new GridLayout(2, 2, 5, 5));
/*
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));
*/

            JButton button1 = new JButton(iconImageJ);
            JButton button2 = new JButton(iconBDV);
            JButton button3 = new JButton("Option 3");
            JButton button4 = new JButton("Option 4");

            button2.addActionListener(e -> {
                System.out.println("closing submenu");
                submenuWindow.dispose();
            });

            panel.add(button1);
            panel.add(button2);
            panel.add(button3);
            panel.add(button4);
        } else {
            // 2x1 horizontal layout
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
            //panel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

            JButton button1 = new JButton(iconImageJ);
            JButton button2 = new JButton(iconBDV);

            button2.addActionListener(e -> {
                System.out.println("closing submenu");
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ContextMenuAroundMouse());
    }
}
