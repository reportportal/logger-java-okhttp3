/*
 * Copyright 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.okhttp3.support;

import com.epam.reportportal.formatting.http.HttpPartFormatter;
import com.epam.reportportal.formatting.http.HttpRequestFormatter;
import com.epam.reportportal.formatting.http.HttpResponseFormatter;
import com.epam.reportportal.formatting.http.entities.BodyType;
import com.epam.reportportal.formatting.http.entities.Cookie;
import com.epam.reportportal.formatting.http.entities.Header;
import com.epam.reportportal.formatting.http.entities.Param;
import okhttp3.*;
import okio.Buffer;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.epam.reportportal.formatting.http.HttpFormatUtils.getBodyType;
import static com.epam.reportportal.formatting.http.HttpFormatUtils.getMimeType;
import static java.util.Optional.ofNullable;

/**
 * Factory class to convert Rest-Assured entities to internal ones.
 */
public class HttpEntityFactory {

	@Nonnull
	private static String toString(@Nonnull RequestBody body) {
		try (Buffer buffer = new Buffer()) {
			body.writeTo(buffer);
			return buffer.readString(ofNullable(body.contentType()).map(MediaType::charset)
					.orElse(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("Error writing in-memory buffer", e);
		}
	}

	@Nonnull
	private static byte[] toBytes(@Nonnull RequestBody body) {
		try (Buffer buffer = new Buffer()) {
			body.writeTo(buffer);
			return buffer.readByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Error writing in-memory buffer", e);
		}
	}

	@Nonnull
	private static String toString(@Nonnull ResponseBody body) {
		try {
			return body.string();
		} catch (IOException e) {
			throw new IllegalStateException("Error reading response body", e);
		}
	}

	@Nonnull
	private static byte[] toBytes(@Nonnull ResponseBody body) {
		try {
			return body.bytes();
		} catch (IOException e) {
			throw new IllegalStateException("Error reading response body", e);
		}
	}

	@Nonnull
	public static HttpRequestFormatter createHttpRequestFormatter(@Nonnull Request request,
			@Nullable Function<String, String> uriConverter, @Nullable Function<Header, String> headerConverter,
			@Nullable Function<Cookie, String> cookieConverter,
			@Nullable Map<String, Function<String, String>> prettiers,
			@Nullable Function<Header, String> partHeaderConverter, @Nonnull Map<String, BodyType> bodyTypeMap) {
		HttpRequestFormatter.Builder builder = new HttpRequestFormatter.Builder(request.method(),
				request.url().toString()
		);
		Optional.of(request.headers())
				.ifPresent(headers -> headers.forEach(h -> builder.addHeader(h.getFirst(), h.getSecond())));
		// TODO: get Cookies somehow
		builder.uriConverter(uriConverter)
				.headerConverter(headerConverter)
				.cookieConverter(cookieConverter)
				.prettiers(prettiers);

		String contentType = request.header(HttpHeaders.CONTENT_TYPE);
		String type = getMimeType(contentType);
		BodyType bodyType = getBodyType(contentType, bodyTypeMap);
		switch (bodyType) {
			case TEXT:
				builder.bodyText(type, ofNullable(request.body()).map(HttpEntityFactory::toString).orElse(null));
				break;
			case FORM:
				ofNullable(request.body()).filter(b -> b instanceof FormBody)
						.map(b -> (FormBody) b)
						.ifPresent(body -> builder.bodyParams(IntStream.range(0, body.size())
								.mapToObj(i -> new Param(body.name(i), body.value(i)))
								.collect(Collectors.toList())));
				break;
			case MULTIPART:
				ofNullable(request.body()).filter(b -> b instanceof MultipartBody)
						.map(b -> (MultipartBody) b)
						.ifPresent(body -> body.parts().forEach(it -> {
							RequestBody partBody = it.body();
							String partMimeType = ofNullable(partBody.contentType()).map(Object::toString)
									.orElse(ContentType.APPLICATION_OCTET_STREAM.getMimeType());
							BodyType bodyPartType = getBodyType(partMimeType, bodyTypeMap);
							HttpPartFormatter.Builder partBuilder;
							if (BodyType.TEXT == bodyPartType) {
								partBuilder = new HttpPartFormatter.Builder(HttpPartFormatter.PartType.TEXT,
										partMimeType,
										toString(partBody)
								);
							} else {
								partBuilder = new HttpPartFormatter.Builder(HttpPartFormatter.PartType.BINARY,
										partMimeType,
										toBytes(partBody)
								);
							}
							ofNullable(it.headers()).ifPresent(headers -> headers.forEach(h -> partBuilder.addHeader(new Header(h.getFirst(),
									h.getSecond()
							))));
							ofNullable(it.body().contentType()).map(MediaType::charset)
									.map(Charset::name)
									.ifPresent(partBuilder::charset);
							partBuilder.headerConverter(partHeaderConverter);
							builder.addBodyPart(partBuilder.build());
						}));
				break;
			default:
				builder.bodyBytes(type, ofNullable(request.body()).map(HttpEntityFactory::toBytes).orElse(null));
		}
		return builder.build();
	}

	@Nonnull
	public static HttpResponseFormatter createHttpResponseFormatter(@Nonnull Response response,
			@Nullable Function<Header, String> headerConverter, @Nullable Function<Cookie, String> cookieConverter,
			@Nullable Map<String, Function<String, String>> prettiers, @Nonnull Map<String, BodyType> bodyTypeMap) {
		HttpResponseFormatter.Builder builder = new HttpResponseFormatter.Builder(response.code(), response.message());
		Optional.of(response.headers())
				.ifPresent(headers -> headers.forEach(h -> builder.addHeader(h.getFirst(), h.getSecond())));
		// TODO: get Cookies somehow
		builder.headerConverter(headerConverter).cookieConverter(cookieConverter).prettiers(prettiers);

		String contentType = response.header(HttpHeaders.CONTENT_TYPE);
		String type = getMimeType(contentType);
		BodyType bodyType = getBodyType(contentType, bodyTypeMap);
		if (BodyType.TEXT == bodyType) {
			builder.bodyText(type, ofNullable(response.body()).map(HttpEntityFactory::toString).orElse(null));
		} else {
			builder.bodyBytes(type, ofNullable(response.body()).map(HttpEntityFactory::toBytes).orElse(null));
		}
		return builder.build();
	}
}
