package sc.fiji.ome.zarr.pyramid;

import org.scijava.Contextual;

public interface Pyramidal extends Contextual
{

	Pyramidal5DImageData< ? > getPyramidal5DImageData();

	default int numResolutions()
	{
		return getPyramidal5DImageData().numResolutionLevels();
	}

	/*
		public List< SourceAndConverter< T > > asSources()
	{
		return data.asSources();
	}

	public int numChannels()
	{
		return data.numChannels();
	}

	public int numTimepoints()
	{
		return data.numTimepoints();
	}

	public Omero getOmeroProperties()
	{
		return data.getOmeroProperties();
	}

	public int numResolutions()
	{
		return data.numResolutionLevels();
	}

	public VoxelDimensions voxelDimensions()
	{
		return data.voxelDimensions();
	}

	public String getPyramidName()
	{
		return data.getName();
	}


	 */

}
