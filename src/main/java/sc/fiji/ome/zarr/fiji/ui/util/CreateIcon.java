package sc.fiji.ome.zarr.fiji.ui.util;

import java.net.URL;
import java.awt.Image;
import javax.swing.ImageIcon;

public class CreateIcon {
	public static ImageIcon fetchAndResizeIcon(String imageUrl) {
		ImageIcon icon = null;
		try {
			icon = new ImageIcon( new ImageIcon(new URL(imageUrl))
						.getImage()
						.getScaledInstance(32, 32, Image.SCALE_SMOOTH)
			);
		} catch (Exception e) {
			// Fallback to an empty image if the requested image can't be loaded
			icon = new ImageIcon();
		}
		return icon;
	}
}