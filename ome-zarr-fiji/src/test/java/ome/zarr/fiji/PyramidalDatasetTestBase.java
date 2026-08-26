/*-
 * #%L
 * OME-Zarr extras for Fiji
 * %%
 * Copyright (C) 2022 - 2026 SciJava developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package ome.zarr.fiji;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.ImgPlus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.scijava.Context;
import org.scijava.convert.ConvertService;

import java.net.URISyntaxException;

import ome.zarr.ZarrTestUtils;
import ome.zarr.imglib2.PyramidContents;

/**
 * Shared tests for the ImageJ Fiji wrapper {@link PyramidalDataset} around a
 * {@link PyramidContents}, covering {@link net.imagej.Dataset}/{@link ImgPlus}
 * exposure, legacy {@link ImagePlus} conversion and resolution-based naming.
 * A concrete class supplies {@link #read(String, Context)} for a specific
 * backend, so each backend is exercised through the dataset wrapper.
 * <p>
 * These tests intentionally live in the {@code ome.zarr.fiji} test package rather
 * than alongside the backend-agnostic {@link ome.zarr.imglib2.PyramidBackendTestBase},
 * so that the {@code ome.zarr.imglib2} core and the backend packages stay free of any
 * dependency on the Fiji/BDV integration layer.
 */
public interface PyramidalDatasetTestBase
{

	PyramidContents< ? > read( String resource, Context context )
			throws URISyntaxException;

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testAsPyramidalDataset( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = read( resource, context );
			PyramidalDataset pyramidalDataset = new PyramidalDataset( context, contents, 0 );
			assertNotNull( pyramidalDataset );
		}
	}

	@ParameterizedTest
	@MethodSource( "ome.zarr.imglib2.PyramidBackendTestBase#omeZarrExamples" )
	default void testAsDataset( String resource ) throws URISyntaxException
	{
		try (Context context = new Context())
		{
			PyramidContents< ? > contents = read( resource, context );
			Dataset ijDataset = new PyramidalDataset( context, contents, 0 );
			assertNotNull( ijDataset );
			ImgPlus< ? > imgPlus = ijDataset.getImgPlus();
			assertNotNull( imgPlus );
			boolean is3D = resource.contains( "5d_testing" )
					|| ( resource.contains( "4d_testing" ) && resource.contains( "xyz" ) )
					|| ( resource.contains( "3d_testing" ) && resource.contains( "xyz" ) );
			assertEquals( 64, imgPlus.dimension( 0 ) );
			assertEquals( 64, imgPlus.dimension( 1 ) );
			if ( is3D )
				assertEquals( 16, imgPlus.dimension( 2 ) );
			assertEquals( ZarrTestUtils.IMAGE_NAME + " (R)", imgPlus.getName() );
		}
	}

	/**
	 * Opens an OME-Zarr dataset and obtains a legacy ImageJ1 {@link ImagePlus}
	 * handle from it. The intermediate IJ2 {@link Dataset} is converted to an
	 * {@code ImagePlus} via SciJava's {@link ConvertService}; the converter
	 * itself is provided by the {@code imagej-legacy} dependency on the classpath.
	 */
	@Test
	default void testOpenAsImagePlusConvertService() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			final PyramidContents< ? > contents =
					read( "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr", context );
			final PyramidalDataset dataset = new PyramidalDataset( context, contents, 0 );
			final ImagePlus imagePlus = dataset.asImagePlus();

			assertNotNull( imagePlus );
			// order of dimensions for imagePlus: width, height, channels, slices, frames
			assertArrayEquals( new int[] { 64, 64, 1, 16, 4 }, imagePlus.getDimensions() );
			assertEquals( dataset.getName(), imagePlus.getTitle() );
		}
	}

	@Test
	default void testOpenAsImagePlusSourceAndConverter() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			final PyramidContents< ? > contents =
					read( "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr", context );
			ImagePlus imagePlus = new PyramidalDataset( context, contents, 0 ).asImagePlus();

			assertNotNull( imagePlus );
			// order of dimensions for imagePlus: width, height, channels, slices, frames
			assertArrayEquals( new int[] { 64, 64, 1, 16, 4 }, imagePlus.getDimensions() );
			imagePlus = new PyramidalDataset( context, contents, 1 ).asImagePlus();
			assertNotNull( imagePlus );
			// order of dimensions for imagePlus: width, height, channels, slices, frames
			assertArrayEquals( new int[] { 32, 32, 1, 8, 4 }, imagePlus.getDimensions() );
		}
	}

	/**
	 * A multi-resolution image gets a {@code " (R)"} suffix in its name (at any
	 * resolution level).
	 */
	@Test
	default void multiResolutionImageNameEndsWithR() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			final PyramidContents< ? > contents =
					read( "ome/zarr/testdata/5d_testing/5d_dataset_v5.ome.zarr", context );
			assertTrue( new PyramidalDataset( context, contents, 0 ).getName().endsWith( " (R)" ) );
			assertTrue( new PyramidalDataset( context, contents, 1 ).getName().endsWith( " (R)" ) );
		}
	}

	/**
	 * A single-resolution image does not get a {@code "(R)"} suffix in its name.
	 */
	@Test
	default void singleResolutionImageNameHasNoR() throws URISyntaxException
	{
		try (Context context = new Context())
		{
			final PyramidContents< ? > contents = read(
					"ome/zarr/testdata/single_resolution_testing/single_resolution_dataset_v5.ome.zarr", context );
			final String name = new PyramidalDataset( context, contents, 0 ).getName();
			assertFalse( name.contains( "(R)" ), "expected no '(R)' in name but was: " + name );
		}
	}
}
