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

import com.epam.reportportal.formatting.http.HttpFormatUtils;
import com.epam.reportportal.formatting.http.HttpPartFormatter;
import com.epam.reportportal.formatting.http.HttpRequestFormatter;
import com.epam.reportportal.formatting.http.HttpResponseFormatter;
import com.epam.reportportal.formatting.http.converters.DefaultCookieConverter;
import com.epam.reportportal.formatting.http.entities.BodyType;
import com.epam.reportportal.formatting.http.entities.Cookie;
import com.epam.reportportal.formatting.http.entities.Header;
import com.epam.reportportal.formatting.http.entities.Param;
import kotlin.Pair;
import okhttp3.*;
import okio.Buffer;
import org.apache.http.entity.ContentType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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

	private static boolean isCookie(Pair<? extends String, ? extends String> h) {
		return "cookie".equalsIgnoreCase(h.getFirst());
	}

	private static boolean isSetCookie(Pair<? extends String, ? extends String> h) {
		return "set-cookie".equalsIgnoreCase(h.getFirst());
	}

	private static Stream<Pair<String, String>> toKeyValue(Pair<? extends String, ? extends String> h) {
		return Arrays.stream(h.getSecond().split(";\\s*")).map(c -> c.split("=", 2)).map(kv -> {
			if (kv.length > 1) {
				try {
					return new Pair<>(kv[0], URLDecoder.decode(kv[1], Charset.defaultCharset().name()));
				} catch (UnsupportedEncodingException e) {
					throw new IllegalStateException(e);
				}
			}
			return new Pair<>(kv[0], "");
		});
	}

	private static Cookie toCookie(Pair<? extends String, ? extends String> h) {
		List<Pair<String, String>> cookie = toKeyValue(h).collect(Collectors.toList());
		Pair<String, String> nameValue = cookie.get(0);
		Map<String, String> cookieMetadata = cookie.subList(1, cookie.size())
				.stream()
				.collect(Collectors.toMap(kv -> kv.getFirst().toLowerCase(Locale.US), Pair<String, String>::getSecond));
		String comment = cookieMetadata.get("comment");
		String path = cookieMetadata.get("path");
		String domain = cookieMetadata.get("domain");
		Long maxAge = cookieMetadata.get("maxage") == null ? null : Long.valueOf(cookieMetadata.get("maxage"));
		Boolean secure = cookieMetadata.containsKey("secure");
		Boolean httpOnly = cookieMetadata.containsKey("httponly");
		// Examples: Tue, 06 Sep 2022 09:32:51 GMT
		//           Wed, 06-Sep-2023 11:22:09 GMT
		Date expiryDate = ofNullable(cookieMetadata.get("expires")).map(d -> {
			try {
				return new SimpleDateFormat(DefaultCookieConverter.DEFAULT_COOKIE_DATE_FORMAT).parse(d.replace(
						'-',
						' '
				));
			} catch (ParseException e) {
				return null;
			}
		}).orElse(null);
		Integer version = cookieMetadata.get("version") == null ? null : Integer.valueOf(cookieMetadata.get("version"));
		String sameSite = cookieMetadata.get("samesite");

		return HttpFormatUtils.toCookie(nameValue.getFirst(),
				nameValue.getSecond(),
				comment,
				path,
				domain,
				maxAge,
				secure,
				httpOnly,
				expiryDate,
				version,
				sameSite
		);
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
		StreamSupport.stream(request.headers().spliterator(), false)
				.filter(h -> !isCookie(h))
				.forEach(h -> builder.addHeader(h.getFirst(), h.getSecond()));
		StreamSupport.stream(request.headers().spliterator(), false)
				.filter(HttpEntityFactory::isCookie)
				.flatMap(HttpEntityFactory::toKeyValue)
				.forEach(h -> builder.addCookie(h.getFirst(), h.getSecond()));
		builder.uriConverter(uriConverter)
				.headerConverter(headerConverter)
				.cookieConverter(cookieConverter)
				.prettiers(prettiers);

		String contentType = ofNullable(request.body()).map(RequestBody::contentType)
				.map(MediaType::toString)
				.orElse(null);
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
		StreamSupport.stream(response.headers().spliterator(), false)
				.filter(h -> !isSetCookie(h))
				.forEach(h -> builder.addHeader(h.getFirst(), h.getSecond()));
		StreamSupport.stream(response.headers().spliterator(), false)
				.filter(HttpEntityFactory::isSetCookie)
				.forEach(h -> builder.addCookie(toCookie(h)));
		builder.headerConverter(headerConverter).cookieConverter(cookieConverter).prettiers(prettiers);

		String contentType = ofNullable(response.body()).map(ResponseBody::contentType)
				.map(MediaType::toString)
				.orElse(null);
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
