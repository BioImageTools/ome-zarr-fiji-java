/*-
 * #%L
 * OME-Zarr reader based on zarr-java
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
package ome.zarr.zarrjava;

import java.net.URI;

import dev.zarr.zarrjava.store.S3Store;
import dev.zarr.zarrjava.store.Store;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Builds the zarr-java {@link S3Store} for an {@code s3:} URI.
 * <p>
 * This is deliberately a class of its own, so the JVM only loads the (large) AWS SDK class graph on the first
 * call to {@link #create}, i.e., only when an {@code s3:} URI is actually opened.
 */
final class S3StoreFactory
{
	private S3StoreFactory()
	{
		// prevent instantiation
	}

	/**
	 * Creates an anonymous-capable S3 store for {@code uri}. Credentials come from
	 * the default AWS chain and fall back to anonymous access, so public buckets
	 * work without configuration.
	 *
	 * @param uri an {@code s3://bucket/key/prefix} URI; the bucket is its host, the
	 *   key prefix its path
	 */
	static Store create( final URI uri )
	{
		final S3Client s3 = S3Client.builder().region( Region.US_EAST_1 )
				.credentialsProvider( AwsCredentialsProviderChain.builder()
						.credentialsProviders( DefaultCredentialsProvider.builder().build(), AnonymousCredentialsProvider.create() )
						.build() )
				.build();
		final String bucket = uri.getHost();
		final String rawPath = uri.getPath();
		final String keyPrefix = rawPath == null ? "" : rawPath.replaceFirst( "^/", "" );
		return new S3Store( s3, bucket, keyPrefix.isEmpty() ? null : keyPrefix );
	}

	/**
	 * Whether {@code t} is a failure raised by the AWS SDK. Lives here rather than
	 * in {@link ZarrJavaPyramidBackend} because it is an AWS reference like any
	 * other: callers must reach it only on the {@code s3:} path.
	 */
	static boolean isSdkException( final Throwable t )
	{
		return t instanceof SdkException;
	}
}
