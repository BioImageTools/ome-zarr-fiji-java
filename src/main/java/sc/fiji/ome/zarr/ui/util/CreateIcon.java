package sc.fiji.ome.zarr.ui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Image;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import javax.swing.ImageIcon;

public class CreateIcon {

    private static final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

    private CreateIcon() {
        // prevent instantiation
    }

    /**
     * Loads an image from the classpath (e.g., in src/main/resources)
     * and scales it to 32x32 pixels.
     *
     * @param resourcePath path to the image relative to the classpath,
     *                     e.g. "/icons/myicon.png"
     * @return a scaled ImageIcon, or an empty one if loading fails
     */
    public static ImageIcon getAndResizeIcon(final String resourcePath) {


        try {
            URL resourceUrl = CreateIcon.class.getResource(resourcePath);
            if (resourceUrl == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }

            Image image = new ImageIcon(resourceUrl)
                    .getImage()
                    .getScaledInstance(32, 32, Image.SCALE_SMOOTH);

            return new ImageIcon(image);
        } catch (Exception e) {
            logger.debug("Failed to load icon from path: " + resourcePath, e);
            // Fallback to an empty image if the resource can't be loaded
            return new ImageIcon();
        }
    }
}
