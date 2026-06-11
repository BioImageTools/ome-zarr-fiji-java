package sc.fiji.ome.zarr.pyramid;

import org.scijava.Contextual;

import mpicbg.spim.data.sequence.VoxelDimensions;
import sc.fiji.ome.zarr.pyramid.metadata.Omero;

public interface Pyramidal extends Contextual
{

	Pyramidal5DImageData< ? > getPyramidal5DImageData();

	default int numResolutions()
	{
		return getPyramidal5DImageData().numResolutionLevels();
	}

	// TODO: the methods below this may be removed
	default int numChannels()
	{
		return getPyramidal5DImageData().numChannels();
	}

	default int numTimepoints()
	{
		return getPyramidal5DImageData().numTimepoints();
	}

	default Omero getOmeroProperties()
	{
		return getPyramidal5DImageData().getOmeroProperties();
	}

	default VoxelDimensions voxelDimensions()
	{
		return getPyramidal5DImageData().voxelDimensions();
	}

	default String getPyramidName()
	{
		return getPyramidal5DImageData().getName();
	}
}
