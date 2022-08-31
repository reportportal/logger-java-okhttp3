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
import com.epam.reportportal.formatting.http.converters.DefaultHttpHeaderConverter;
import com.epam.reportportal.formatting.http.converters.DefaultUriConverter;
import com.epam.reportportal.formatting.http.entities.BodyType;
import com.epam.reportportal.formatting.http.entities.Cookie;
import com.epam.reportportal.formatting.http.entities.Header;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.okhttp3.support.HttpEntityFactory;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.epam.reportportal.formatting.http.Constants.BODY_TYPE_MAP;
import static com.epam.reportportal.formatting.http.Constants.DEFAULT_PRETTIERS;

public class ReportPortalOkHttp3LoggingInterceptor extends AbstractHttpFormatter<ReportPortalOkHttp3LoggingInterceptor>
		implements Interceptor {

	private final List<Predicate<Request>> requestFilters = new CopyOnWriteArrayList<>();

	private Map<String, Function<String, String>> contentPrettiers = DEFAULT_PRETTIERS;

	private Map<String, BodyType> bodyTypeMap = BODY_TYPE_MAP;

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
			@Nullable Function<Header, String> headerConvertFunction,
			@Nullable Function<Header, String> partHeaderConvertFunction,
			@Nullable Function<Cookie, String> cookieConvertFunction,
			@Nullable Function<String, String> uriConverterFunction) {
		super(defaultLogLevel,
				headerConvertFunction,
				partHeaderConvertFunction,
				cookieConvertFunction,
				uriConverterFunction
		);
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
			@Nullable Function<Header, String> headerConvertFunction,
			@Nullable Function<Header, String> partHeaderConvertFunction,
			@Nullable Function<Cookie, String> cookieConvertFunction) {
		this(defaultLogLevel,
				headerConvertFunction,
				partHeaderConvertFunction,
				cookieConvertFunction,
				DefaultUriConverter.INSTANCE
		);
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
			@Nullable Function<Header, String> headerConvertFunction,
			@Nullable Function<Header, String> partHeaderConvertFunction) {
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

	@Nonnull
	@Override
	public Response intercept(@Nonnull Chain chain) throws IOException {
		Request request = chain.request();
		if (requestFilters.stream().anyMatch(f -> f.test(request))) {
			return chain.proceed(chain.request());
		}
		emitLog(HttpEntityFactory.createHttpRequestFormatter(request,
				uriConverter,
				headerConverter,
				cookieConverter,
				contentPrettiers,
				partHeaderConverter,
				bodyTypeMap
		));
		Response response = chain.proceed(chain.request());
		emitLog(HttpEntityFactory.createHttpResponseFormatter(response,
				headerConverter,
				cookieConverter,
				contentPrettiers,
				bodyTypeMap
		));
		return response;
	}

	public ReportPortalOkHttp3LoggingInterceptor setBodyTypeMap(@Nonnull Map<String, BodyType> typeMap) {
		this.bodyTypeMap = Collections.unmodifiableMap(new HashMap<>(typeMap));
		return this;
	}

	public ReportPortalOkHttp3LoggingInterceptor setContentPrettiers(
			@Nonnull Map<String, Function<String, String>> contentPrettiers) {
		this.contentPrettiers = Collections.unmodifiableMap(new HashMap<>(contentPrettiers));
		return this;
	}

	public ReportPortalOkHttp3LoggingInterceptor addRequestFilter(@Nonnull Predicate<Request> requestFilter) {
		requestFilters.add(requestFilter);
		return this;
	}
}
