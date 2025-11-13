package sc.fiji.ome.zarr.fiji.ui.util;

import java.net.URL;
import java.awt.Image;
import javax.swing.ImageIcon;

public class CreateIcon {
	final private ImageIcon icon;

	public CreateIcon(String imageUrl) {
		ImageIcon localIcon = null;
		try {
			localIcon = new ImageIcon( new ImageIcon(new URL(imageUrl))
						.getImage()
						.getScaledInstance(32, 32, Image.SCALE_SMOOTH)
			);
		} catch (Exception e) {
			// Fallback if image can't be loaded
			localIcon = new ImageIcon();
		}
		icon = localIcon;
	}

	public ImageIcon getIcon() {
		return icon;
	}
}