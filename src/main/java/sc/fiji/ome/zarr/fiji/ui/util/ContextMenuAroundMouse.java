package sc.fiji.ome.zarr.fiji.ui.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ContextMenuAroundMouse extends JFrame {
    private JWindow submenuWindow;

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
    }

    private void showSubmenu() {
        // Close existing submenu if open
        if (submenuWindow != null && submenuWindow.isVisible()) {
            submenuWindow.dispose();
        }

        // Get current mouse position
        Point mouseLocation = MouseInfo.getPointerInfo().getLocation();

        // Create undecorated window (no title bar, borders, etc.)
        submenuWindow = new JWindow(this);

        // Create panel with two buttons
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

        JButton button1 = new JButton("Option 1");
        JButton button2 = new JButton("Option 2");

        // Add action listeners to buttons
        button1.addActionListener(e -> {
            System.out.println("Option 1 clicked");
            submenuWindow.dispose();
        });

        button2.addActionListener(e -> {
            System.out.println("Option 2 clicked");
            submenuWindow.dispose();
        });

        panel.add(button1);
        panel.add(button2);

        submenuWindow.add(panel);
        submenuWindow.pack(); // Size to fit contents

        // Position at mouse cursor
        submenuWindow.setLocation(mouseLocation);

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
