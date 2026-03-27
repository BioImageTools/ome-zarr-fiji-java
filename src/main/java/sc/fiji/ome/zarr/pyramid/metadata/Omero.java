package sc.fiji.ome.zarr.pyramid.metadata;

import java.util.List;

/**
 * The class is used to represent omero metadata.
 */
@SuppressWarnings( "all" )
public class Omero
{
	// Top-level Omero class
	public int id;

	public String name;

	public Rdefs rdefs;

	public List< Channel > channels;

	public static class Rdefs
	{
		public int defaultT;

		public int defaultZ;

		public String model;
	}

	public static class Channel
	{
		public boolean active;

		public double coefficient;

		public String color;

		public String family;

		public boolean inverted;

		public String label;

		public Window window;

		public static class Window
		{
			public double start;

			public double end;

			public double min;

			public double max;
		}
	}
}
