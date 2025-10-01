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

package com.epam.reportportal.okhttp3;

import com.epam.reportportal.formatting.AbstractHttpFormatter;
import com.epam.reportportal.formatting.http.converters.DefaultCookieConverter;
import com.epam.reportportal.formatting.http.converters.DefaultFormParamConverter;
import com.epam.reportportal.formatting.http.converters.DefaultHttpHeaderConverter;
import com.epam.reportportal.formatting.http.converters.DefaultUriConverter;
import com.epam.reportportal.formatting.http.entities.Cookie;
import com.epam.reportportal.formatting.http.entities.Header;
import com.epam.reportportal.formatting.http.entities.Param;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.okhttp3.support.HttpEntityFactory;
import okhttp3.*;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

public class ReportPortalOkHttp3LoggingInterceptor extends AbstractHttpFormatter<ReportPortalOkHttp3LoggingInterceptor>
		implements Interceptor {

	private final List<Predicate<Request>> requestFilters = new CopyOnWriteArrayList<>();

	protected final Function<Param, String> paramConverter;

	/**
	 * Create OKHTTP3 Interceptor with the log level and different converters.
	 *
	 * @param defaultLogLevel           log level on which OKHTTP3 requests/responses will appear on Report Portal
	 * @param headerConvertFunction     if you want to preprocess your HTTP Headers before they appear on Report Portal
	 *                                  provide this custom function for the class, default function formats it like
	 *                                  that: <code>header.getName() + ": " + header.getValue()</code>
	 * @param partHeaderConvertFunction the same as for HTTP Headers, but for parts in Multipart request
	 * @param cookieConvertFunction     the same as 'headerConvertFunction' param but for Cookies, default function
	 *                                  formats Cookies with <code>toString</code> method
	 * @param uriConverterFunction      the same as 'headerConvertFunction' param but for URI, default function returns
	 *                                  URI "as is"
	 * @param paramConverter            the same as 'headerConvertFunction' param but for Web Form Params, default function returns
	 *                                  <code>param.getName() + ": " + param.getValue()</code>
	 */
	public ReportPortalOkHttp3LoggingInterceptor(@Nonnull LogLevel defaultLogLevel,
			@Nullable Function<Header, String> headerConvertFunction, @Nullable Function<Header, String> partHeaderConvertFunction,
			@Nullable Function<Cookie, String> cookieConvertFunction, @Nullable Function<String, String> uriConverterFunction,
			@Nullable Function<Param, String> paramConverter) {
		super(defaultLogLevel, headerConvertFunction, partHeaderConvertFunction, cookieConvertFunction, uriConverterFunction);
		this.paramConverter = paramConverter != null ? paramConverter : DefaultFormParamConverter.INSTANCE;
	}

	/**
	 * Create OKHTTP3 Interceptor with the log level and different converters.
	 *
	 * @param defaultLogLevel           log level on which OKHTTP3 requests/responses will appear on Report Portal
	 * @param headerConvertFunction     if you want to preprocess your HTTP Headers before they appear on Report Portal
	 *                                  provide this custom function for the class, default function formats it like
	 *                                  that: <code>header.getName() + ": " + header.getValue()</code>
	 * @param partHeaderConvertFunction the same as for HTTP Headers, but for parts in Multipart request
	 * @param cookieConvertFunction     the same as 'headerConvertFunction' param but for Cookies, default function
	 *                                  formats Cookies with <code>toString</code> method
	 * @param uriConverterFunction      the same as 'headerConvertFunction' param but for URI, default function returns
	 *                                  URI "as is"
	 */
	public ReportPortalOkHttp3LoggingInterceptor(@Nonnull LogLevel defaultLogLevel,
			@Nullable Function<Header, String> headerConvertFunction, @Nullable Function<Header, String> partHeaderConvertFunction,
			@Nullable Function<Cookie, String> cookieConvertFunction, @Nullable Function<String, String> uriConverterFunction) {
		this(defaultLogLevel, headerConvertFunction, partHeaderConvertFunction, cookieConvertFunction, uriConverterFunction,
				DefaultFormParamConverter.INSTANCE);
	}

	/**
	 * Create OKHTTP3 Interceptor with the log level and different converters.
	 *
	 * @param defaultLogLevel           log level on which OKHTTP3 requests/responses will appear on Report Portal
	 * @param headerConvertFunction     if you want to preprocess your HTTP Headers before they appear on Report Portal
	 *                                  provide this custom function for the class, default function formats it like
	 *                                  that: <code>header.getName() + ": " + header.getValue()</code>
	 * @param partHeaderConvertFunction the same as for HTTP Headers, but for parts in Multipart request
	 * @param cookieConvertFunction     the same as 'headerConvertFunction' param but for Cookies, default function
	 *                                  formats Cookies with <code>toString</code> method
	 */
	public ReportPortalOkHttp3LoggingInterceptor(@Nonnull LogLevel defaultLogLevel,
			@Nullable Function<Header, String> headerConvertFunction, @Nullable Function<Header, String> partHeaderConvertFunction,
			@Nullable Function<Cookie, String> cookieConvertFunction) {
		this(defaultLogLevel, headerConvertFunction, partHeaderConvertFunction, cookieConvertFunction, DefaultUriConverter.INSTANCE);
	}

	/**
	 * Create OKHTTP3 Interceptor with the log level and header converters.
	 *
	 * @param defaultLogLevel           log level on which OKHTTP3 requests/responses will appear on Report Portal
	 * @param headerConvertFunction     if you want to preprocess your HTTP Headers before they appear on Report Portal
	 *                                  provide this custom function for the class, default function formats it like
	 *                                  that: <code>header.getName() + ": " + header.getValue()</code>
	 * @param partHeaderConvertFunction the same as for HTTP Headers, but for parts in Multipart request
	 */
	public ReportPortalOkHttp3LoggingInterceptor(@Nonnull LogLevel defaultLogLevel,
			@Nullable Function<Header, String> headerConvertFunction, @Nullable Function<Header, String> partHeaderConvertFunction) {
		this(defaultLogLevel, headerConvertFunction, partHeaderConvertFunction, DefaultCookieConverter.INSTANCE);
	}

	/**
	 * Create OKHTTP3 Interceptor with the log level.
	 *
	 * @param defaultLogLevel log level on which OKHTTP3 requests/responses will appear on Report Portal
	 */
	public ReportPortalOkHttp3LoggingInterceptor(@Nonnull LogLevel defaultLogLevel) {
		this(defaultLogLevel, DefaultHttpHeaderConverter.INSTANCE, DefaultHttpHeaderConverter.INSTANCE);
	}

	@SuppressWarnings("resource")
	private static Response[] duplicateResponse(Response from) throws IOException {
		Response.Builder responseBuilder1 = from.newBuilder();
		Response.Builder responseBuilder2 = from.newBuilder();
		ResponseBody body = from.body();
		if (body != null) {
			MediaType type = body.contentType();
			byte[] bytes = body.bytes();
			responseBuilder1.body(ResponseBody.create(bytes, type));
			responseBuilder2.body(ResponseBody.create(bytes, type));
		}
		return new Response[] { responseBuilder1.build(), responseBuilder2.build() };
	}

	@Nonnull
	@Override
	public Response intercept(@Nonnull Chain chain) throws IOException {
		Request request = chain.request();
		if (requestFilters.stream().anyMatch(f -> f.test(request))) {
			return chain.proceed(chain.request());
		}
		emitLog(HttpEntityFactory.createHttpRequestFormatter(
				request,
				uriConverter,
				headerConverter,
				cookieConverter,
				paramConverter,
				getContentPrettifiers(),
				partHeaderConverter,
				getBodyTypeMap()
		));
		Response response = chain.proceed(chain.request());
		Response[] responses = duplicateResponse(response);
		emitLog(HttpEntityFactory.createHttpResponseFormatter(
				responses[0],
				headerConverter,
				cookieConverter,
				getContentPrettifiers(),
				getBodyTypeMap()
		));
		return responses[1];
	}

	public ReportPortalOkHttp3LoggingInterceptor addRequestFilter(@Nonnull Predicate<Request> requestFilter) {
		requestFilters.add(requestFilter);
		return this;
	}
}
