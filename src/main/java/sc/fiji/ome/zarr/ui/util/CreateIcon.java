package sc.fiji.ome.zarr.ui.util;

import java.net.URI;
import java.net.URL;
import java.awt.Image;
import javax.swing.ImageIcon;

public class CreateIcon {

    private CreateIcon() {
        // prevent instantiation
    }

    public static ImageIcon fetchAndResizeIcon(String imageUrl) {
        try {
            // Use URI for stricter validation and convert to URL
            URL url = URI.create(imageUrl).toURL();
            Image image = new ImageIcon(url).getImage()
                    .getScaledInstance(32, 32, Image.SCALE_SMOOTH);
            return new ImageIcon(image);
        } catch (Exception e) {
            // Fallback to an empty image if the requested image can't be loaded
            return new ImageIcon();
        }
    }
}
