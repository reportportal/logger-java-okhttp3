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

import com.epam.reportportal.formatting.http.Constants;
import com.epam.reportportal.formatting.http.prettifiers.JsonPrettifier;
import com.epam.reportportal.formatting.http.prettifiers.XmlPrettifier;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.message.ReportPortalMessage;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.step.StepReporter;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.reportportal.utils.files.Utils;
import com.epam.reportportal.utils.http.ContentType;
import okhttp3.*;
import okio.BufferedSink;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Optional.ofNullable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ReportPortalOkHttp3LoggingInterceptorTest {

	private static final String IMAGE = "pug/lucky.jpg";
	private static final String HTML_TYPE = "text/html";
	private static final String JSON_TYPE = "application/json";
	private static final String METHOD = "POST";
	private static final String URI = "http://docker.local:8080/app";
	private static final int STATUS_CODE = 201;
	private static final String EMPTY_REQUEST = "**>>> REQUEST**\n" + METHOD + " to " + URI;
	private static final String EMPTY_RESPONSE = "**<<< RESPONSE**\n" + STATUS_CODE;
	private static final String HTTP_HEADER = "Content-Type";
	private static final String HTTP_HEADER_VALUE = JSON_TYPE;

	private static Interceptor.Chain getChain(Request request, Response response) {
		return new Interceptor.Chain() {
			private int writeTimeout = 1000;
			private int readTimeout = 1000;
			private int connectionTimeout = 1000;

			@Override
			public int writeTimeoutMillis() {
				return writeTimeout;
			}

			@NotNull
			@Override
			public Interceptor.Chain withWriteTimeout(int i, @NotNull TimeUnit timeUnit) {
				long result = timeUnit.toMillis(i);
				writeTimeout = result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
				return this;
			}

			@NotNull
			@Override
			public Interceptor.Chain withReadTimeout(int i, @NotNull TimeUnit timeUnit) {
				long result = timeUnit.toMillis(i);
				readTimeout = result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
				return this;
			}

			@NotNull
			@Override
			public Interceptor.Chain withConnectTimeout(int i, @NotNull TimeUnit timeUnit) {
				long result = timeUnit.toMillis(i);
				connectionTimeout = result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
				return this;
			}

			@NotNull
			@Override
			public Request request() {
				return request;
			}

			@Override
			public int readTimeoutMillis() {
				return readTimeout;
			}

			@NotNull
			@Override
			public Response proceed(@NotNull Request request) {
				return response;
			}

			@Nullable
			@Override
			public Connection connection() {
				return mock(Connection.class);
			}

			@Override
			public int connectTimeoutMillis() {
				return connectionTimeout;
			}

			@NotNull
			@Override
			public Call call() {
				return mock(Call.class);
			}
		};
	}

	public static Iterable<Object[]> requestData() {
		return Arrays.asList(
				new Object[] { JSON_TYPE, "{\"object\": {\"key\": \"value\"}}", "{\"object\": {\"key\": \"value\"}}",
						JsonPrettifier.INSTANCE, null, null },
				new Object[] { "application/xml", "<test><key><value>value</value></key></test>",
						"<test><key><value>value</value></key></test>", XmlPrettifier.INSTANCE, null, null }
		);
	}

	private void runChain(Request request, Response response, Consumer<MockedStatic<ReportPortal>> mocks, Interceptor interceptor)
			throws IOException {
		try (MockedStatic<ReportPortal> utilities = Mockito.mockStatic(ReportPortal.class)) {
			mocks.accept(utilities);
			interceptor.intercept(getChain(request, response));
		}
	}

	private void runChain(Request request, Response response, Consumer<MockedStatic<ReportPortal>> mocks) throws IOException {
		runChain(request, response, mocks, new ReportPortalOkHttp3LoggingInterceptor(LogLevel.INFO));
	}

	private List<String> runChainTextMessageCapture(Request request, Response response) throws IOException {
		ArgumentCaptor<String> logCapture = ArgumentCaptor.forClass(String.class);
		runChain(
				request,
				response,
				mock -> mock.when(() -> ReportPortal.emitLog(logCapture.capture(), anyString(), any(Instant.class))).thenReturn(Boolean.TRUE)
		);
		return logCapture.getAllValues();
	}

	private List<ReportPortalMessage> runChainBinaryMessageCapture(Request request, Response response) throws IOException {
		ArgumentCaptor<ReportPortalMessage> logCapture = ArgumentCaptor.forClass(ReportPortalMessage.class);
		runChain(
				request,
				response,
				mock -> mock.when(() -> ReportPortal.emitLog(logCapture.capture(), anyString(), any(Instant.class))).thenReturn(Boolean.TRUE)
		);
		return logCapture.getAllValues();
	}

	private Triple<List<String>, List<String>, List<ReportPortalMessage>> runChainComplexMessageCapture(Request request, Response response,
			Interceptor interceptor) throws IOException {
		ArgumentCaptor<String> stepCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<ReportPortalMessage> messageArgumentCaptor = ArgumentCaptor.forClass(ReportPortalMessage.class);
		try (MockedStatic<Launch> utilities = Mockito.mockStatic(Launch.class)) {
			Launch launch = mock(Launch.class);
			StepReporter reporter = mock(StepReporter.class);
			utilities.when(Launch::currentLaunch).thenReturn(launch);
			when(launch.getStepReporter()).thenReturn(reporter);
			when(reporter.sendStep(any(ItemStatus.class), stepCaptor.capture())).thenReturn(CommonUtils.createMaybeUuid());
			runChain(
					request, response, mock -> {
						mock.when(() -> ReportPortal.emitLog(stringArgumentCaptor.capture(), anyString(), any(Instant.class)))
								.thenReturn(Boolean.TRUE);
						mock.when(() -> ReportPortal.emitLog(messageArgumentCaptor.capture(), anyString(), any(Instant.class)))
								.thenReturn(Boolean.TRUE);
					}, interceptor
			);
		}
		return Triple.of(stepCaptor.getAllValues(), stringArgumentCaptor.getAllValues(), messageArgumentCaptor.getAllValues());
	}

	@SuppressWarnings("SameParameterValue")
	private Triple<List<String>, List<String>, List<ReportPortalMessage>> runChainComplexMessageCapture(Request requestSpecification,
			Response responseObject) throws IOException {
		return runChainComplexMessageCapture(
				requestSpecification,
				responseObject,
				new ReportPortalOkHttp3LoggingInterceptor(LogLevel.INFO)
		);
	}

	private static Request mockBasicRequest(@Nullable String contentType, @Nonnull Headers headers, @Nonnull RequestBody body) {
		Request request = mock(Request.class);
		when(request.method()).thenReturn(METHOD);
		when(request.url()).thenReturn(HttpUrl.parse(URI));
		when(request.headers()).thenReturn(headers);
		if (contentType != null) {
			when(request.body()).thenReturn(body);
			when(body.contentType()).thenReturn(MediaType.parse(contentType));
		}
		when(request.body()).thenReturn(body);
		return request;
	}

	private static Request mockBasicRequest(@Nullable String contentType, @Nonnull Headers headers) {
		return mockBasicRequest(contentType, headers, mock(RequestBody.class));
	}

	private static Request mockBasicRequest(String contentType) {
		return mockBasicRequest(contentType, new Headers.Builder().build());
	}

	private static Response createBasicResponse(@Nullable String contentType, @Nonnull Headers headers, @Nullable ResponseBody body) {
		Response.Builder builder = new Response.Builder();
		builder.headers(headers)
				.code(STATUS_CODE)
				.body(body)
				.request(mockBasicRequest(contentType))
				.protocol(Protocol.HTTP_1_1)
				.message("");
		return builder.build();
	}

	private static Response createBasicResponse(@Nullable String contentType, @Nonnull Headers headers) {
		return createBasicResponse(contentType, headers, null);
	}

	private static Response createBasicResponse(String contentType) {
		return createBasicResponse(contentType, new Headers.Builder().build());
	}

	@Test
	public void test_logger_null_values() throws IOException {
		Request request = mockBasicRequest(null);
		Response response = createBasicResponse(null);

		List<Object> logs = new ArrayList<>();
		logs.addAll(runChainBinaryMessageCapture(request, response));
		logs.addAll(runChainTextMessageCapture(request, response));
		assertThat(logs, hasSize(2)); // Request + Response

		assertThat(((ReportPortalMessage) logs.get(0)).getMessage(), equalTo(EMPTY_REQUEST));
		assertThat(logs.get(1), equalTo(EMPTY_RESPONSE));
	}

	@ParameterizedTest
	@MethodSource("requestData")
	public void test_logger_text_body(String mimeType, String requestBodyStr, String responseBodyStr, Function<String, String> prettier)
			throws IOException {
		RequestBody requestBody = mock(RequestBody.class);
		doAnswer(i -> {
			BufferedSink sink = i.getArgument(0);
			sink.writeString(requestBodyStr, StandardCharsets.UTF_8);
			return null;
		}).when(requestBody).writeTo(any());
		Request request = mockBasicRequest(mimeType, new Headers.Builder().build(), requestBody);

		ResponseBody responseBody = ResponseBody.create(requestBodyStr, MediaType.parse(mimeType));
		Response response = createBasicResponse(mimeType, new Headers.Builder().build(), responseBody);

		List<String> logs = runChainTextMessageCapture(request, response);
		assertThat(logs, hasSize(2)); // Request + Response

		String expectedRequest = EMPTY_REQUEST + "\n\n**Body**\n```\n" + prettier.apply(requestBodyStr) + "\n```";
		String requestLog = logs.get(0);
		assertThat(requestLog, equalTo(expectedRequest));

		String expectedResponse = EMPTY_RESPONSE + "\n\n**Body**\n```\n" + prettier.apply(responseBodyStr) + "\n```";
		String responseLog = logs.get(1);
		assertThat(responseLog, equalTo(expectedResponse));
	}

	public static Iterable<Object[]> testTypes() {
		return Arrays.asList(new Object[] { HTML_TYPE }, new Object[] { null });
	}

	@ParameterizedTest
	@MethodSource("testTypes")
	public void test_logger_headers(String contentType) throws IOException {
		Headers headers = new Headers.Builder().add(HTTP_HEADER, HTTP_HEADER_VALUE).build();
		Request request = mockBasicRequest(contentType, headers);
		Response response = createBasicResponse(contentType, headers);

		List<Object> logs = new ArrayList<>();
		logs.addAll(runChainBinaryMessageCapture(request, response));
		logs.addAll(runChainTextMessageCapture(request, response));
		assertThat(logs, hasSize(2)); // Request + Response

		String headerString = "\n\n**Headers**\n" + HTTP_HEADER + ": " + HTTP_HEADER_VALUE;

		if (contentType == null) {
			assertThat(((ReportPortalMessage) logs.get(0)).getMessage(), equalTo(EMPTY_REQUEST + headerString));
		} else {
			assertThat(logs.get(0), equalTo(EMPTY_REQUEST + headerString));
		}
		assertThat(logs.get(1), equalTo(EMPTY_RESPONSE + headerString));
	}

	@Test
	public void test_logger_cookies() throws IOException {
		Headers requestHeaders = new Headers.Builder().add("Cookie", "test=value").build();
		Request request = mockBasicRequest(HTML_TYPE, requestHeaders);
		String expiryDate = "Tue, 06 Sep 2022 09:32:51 UTC";
		Headers responseHeaders = new Headers.Builder().add(
				"Set-cookie",
				"test=value; expires=" + expiryDate + "; path=/; secure; httponly"
		).build();
		Response response = createBasicResponse(HTML_TYPE, responseHeaders);

		List<String> logs = runChainTextMessageCapture(request, response);
		assertThat(logs, hasSize(2)); // Request + Response

		String requestHeaderString = "\n\n**Cookies**\n" + "test: value";
		String responseHeaderString = "\n\n**Cookies**\n" + "test: value; Path=/; Secure=true; HttpOnly=true; Expires=" + expiryDate;

		assertThat(logs.get(0), equalTo(EMPTY_REQUEST + requestHeaderString));
		assertThat(logs.get(1), equalTo(EMPTY_RESPONSE + responseHeaderString));
	}

	@ParameterizedTest
	@MethodSource("testTypes")
	public void test_logger_headers_and_cookies(String contentType) throws IOException {

		Headers headers = new Headers.Builder().add(HTTP_HEADER, HTTP_HEADER_VALUE).add("Cookie", "test=value; tz=Europe%2FMinsk").build();
		String expiryDate1 = "Tue, 06 Sep 2022 09:32:51 UTC";
		String expiryDate2 = "Tue, 06 Sep 2022 09:32:51 UTC";
		Headers responseHeaders = new Headers.Builder().add(HTTP_HEADER, HTTP_HEADER_VALUE)
				.add("Set-cookie", "test=value; comment=test comment; expires=" + expiryDate1 + "; path=/; version=1")
				.add("Set-cookie", "tz=Europe%2FMinsk; path=/; expires=" + expiryDate2 + "; secure; HttpOnly; SameSite=Lax")
				.build();
		Request request = mockBasicRequest(contentType, headers);
		Response response = createBasicResponse(contentType, responseHeaders);

		List<Object> logs = new ArrayList<>();
		logs.addAll(runChainBinaryMessageCapture(request, response));
		logs.addAll(runChainTextMessageCapture(request, response));
		assertThat(logs, hasSize(2)); // Request + Response

		String requestHeaderString =
				"\n\n**Headers**\n" + HTTP_HEADER + ": " + HTTP_HEADER_VALUE + "\n\n**Cookies**\n" + "test: value\n" + "tz: Europe/Minsk";

		String responseHeaderString = "\n\n**Headers**\n" + HTTP_HEADER + ": " + HTTP_HEADER_VALUE + "\n\n**Cookies**\n"
				+ "test: value; Comment=test comment; Path=/; Expires=" + expiryDate1 + "; Version=1\n"
				+ "tz: Europe/Minsk; Path=/; Secure=true; HttpOnly=true; Expires=" + expiryDate2 + "; SameSite=Lax";

		if (contentType == null) {
			assertThat(((ReportPortalMessage) logs.get(0)).getMessage(), equalTo(EMPTY_REQUEST + requestHeaderString));
		} else {
			assertThat(logs.get(0), equalTo(EMPTY_REQUEST + requestHeaderString));
		}
		assertThat(logs.get(1), equalTo(EMPTY_RESPONSE + responseHeaderString));
	}

	@Test
	public void test_logger_empty_image_body() throws IOException {
		String mimeType = ContentType.IMAGE_JPEG;
		Request request = mockBasicRequest(mimeType);
		RequestBody requestBody = mock(RequestBody.class);
		when(request.body()).thenReturn(requestBody);
		when(requestBody.contentType()).thenReturn(MediaType.parse(mimeType));

		ResponseBody responseBody = mock(ResponseBody.class);
		when(responseBody.bytes()).thenReturn(new byte[0]);
		Response response = createBasicResponse(mimeType, new Headers.Builder().build(), responseBody);

		List<Object> logs = new ArrayList<>();
		logs.addAll(runChainBinaryMessageCapture(request, response));
		logs.addAll(runChainTextMessageCapture(request, response));
		assertThat(logs, hasSize(2)); // Request + Response
		assertThat(((ReportPortalMessage) logs.get(0)).getMessage(), equalTo(EMPTY_REQUEST));
		assertThat(((ReportPortalMessage) logs.get(1)).getMessage(), equalTo(EMPTY_RESPONSE));
	}

	@SuppressWarnings("SameParameterValue")
	private byte[] getResource(String imagePath) {
		return ofNullable(this.getClass().getClassLoader().getResourceAsStream(imagePath)).map(is -> {
			try {
				return Utils.readInputStreamToBytes(is);
			} catch (IOException e) {
				return null;
			}
		}).orElse(null);
	}

	private static final String IMAGE_TYPE = "image/jpeg";
	private static final String WILDCARD_TYPE = "*/*";

	@ParameterizedTest
	@ValueSource(strings = { IMAGE_TYPE, WILDCARD_TYPE })
	public void test_logger_image_body(String mimeType) throws IOException {
		Request request = mockBasicRequest(mimeType);
		byte[] image = getResource(IMAGE);
		RequestBody requestBody = mock(RequestBody.class);
		when(request.body()).thenReturn(requestBody);
		when(requestBody.contentType()).thenReturn(MediaType.parse(mimeType));
		doAnswer(i -> {
			BufferedSink sink = i.getArgument(0);
			sink.write(image);
			return null;
		}).when(requestBody).writeTo(any(BufferedSink.class));

		ResponseBody responseBody = ResponseBody.create(image, MediaType.parse(mimeType));
		Response response = createBasicResponse(mimeType, new Headers.Builder().build(), responseBody);

		List<ReportPortalMessage> logs = runChainBinaryMessageCapture(request, response);
		assertThat(logs, hasSize(2)); // Request + Response
		assertThat(logs.get(0).getMessage(), equalTo(EMPTY_REQUEST));
		assertThat(logs.get(1).getMessage(), equalTo(EMPTY_RESPONSE));

		assertThat(logs.get(0).getData().getMediaType(), equalTo(mimeType));
		assertThat(logs.get(1).getData().getMediaType(), equalTo(mimeType));

		assertThat(logs.get(0).getData().read(), equalTo(image));
		assertThat(logs.get(1).getData().read(), equalTo(image));
	}

	@Test
	public void test_logger_null_response() throws IOException {
		String mimeType = ContentType.IMAGE_JPEG;
		Request request = mockBasicRequest(mimeType);
		when(request.headers()).thenReturn(new Headers.Builder().build());
		when(request.body()).thenReturn(null);

		List<String> logs = runChainTextMessageCapture(request, createBasicResponse(null));
		assertThat(logs, hasSize(2)); // Request + Response
		assertThat(logs.get(0), equalTo(EMPTY_REQUEST));
		assertThat(logs.get(1), equalTo(EMPTY_RESPONSE));
	}

	@Test
	public void test_logger_empty_multipart() throws IOException {
		String mimeType = ContentType.MULTIPART_FORM_DATA;
		Request requestSpecification = mockBasicRequest(mimeType, new Headers.Builder().build(), mock(MultipartBody.class));

		List<String> logs = runChainTextMessageCapture(requestSpecification, createBasicResponse(null));
		assertThat(logs, hasSize(2)); // Request + Response
		assertThat(logs.get(0), equalTo(EMPTY_REQUEST));
	}

	private MultipartBody.Builder getBinaryPart(String mimeType, String filePath) throws IOException {
		byte[] data = getResource(filePath);
		RequestBody body = mock(RequestBody.class);
		when(body.contentType()).thenReturn(MediaType.parse(mimeType));
		doAnswer(i -> {
			BufferedSink sink = i.getArgument(0);
			sink.write(data);
			return null;
		}).when(body).writeTo(any());
		return new MultipartBody.Builder().addFormDataPart("file", filePath, body);
	}

	@SuppressWarnings("SameParameterValue")
	private MultipartBody getBinaryBody(String mimeType, String filePath) throws IOException {
		return getBinaryPart(mimeType, filePath).setType(Objects.requireNonNull(MediaType.parse(ContentType.MULTIPART_FORM_DATA))).build();
	}

	@Test
	public void test_logger_image_multipart() throws IOException {
		byte[] image = getResource(IMAGE);
		String imageType = ContentType.IMAGE_JPEG;
		MultipartBody body = getBinaryBody(ContentType.IMAGE_JPEG, IMAGE);
		String mimeType = ContentType.MULTIPART_FORM_DATA;
		Request request = mockBasicRequest(mimeType);
		when(request.body()).thenReturn(body);

		Triple<List<String>, List<String>, List<ReportPortalMessage>> logs = runChainComplexMessageCapture(
				request,
				createBasicResponse(null)
		);
		assertThat(logs.getLeft(), hasSize(1));
		assertThat(logs.getMiddle(), hasSize(1));
		assertThat(logs.getRight(), hasSize(1));

		assertThat(logs.getLeft().get(0), equalTo(EMPTY_REQUEST));
		assertThat(logs.getMiddle().get(0), equalTo(EMPTY_RESPONSE));
		assertThat(
				logs.getRight().get(0).getMessage(),
				equalTo(Constants.HEADERS_TAG + Constants.LINE_DELIMITER
						+ "Content-Disposition: form-data; name=\"file\"; filename=\"pug/lucky.jpg\"" + Constants.LINE_DELIMITER
						+ Constants.LINE_DELIMITER + Constants.BODY_PART_TAG + "\n" + imageType)
		);
		assertThat(logs.getRight().get(0).getData().getMediaType(), equalTo(imageType));
		assertThat(logs.getRight().get(0).getData().read(), equalTo(image));
	}

	@SuppressWarnings("SameParameterValue")
	private MultipartBody getBinaryTextBody(String textType, String text, String binaryType, String filePath) throws IOException {
		MultipartBody.Builder builder = getBinaryPart(binaryType, filePath);
		RequestBody body = mock(RequestBody.class);
		doAnswer(i -> {
			BufferedSink sink = i.getArgument(0);
			sink.writeString(text, StandardCharsets.UTF_8);
			return null;
		}).when(body).writeTo(any());
		when(body.contentType()).thenReturn(MediaType.parse(textType));
		return builder.addPart(body).build();
	}

	@Test
	public void test_logger_text_and_image_multipart() throws IOException {
		byte[] image = getResource(IMAGE);
		String requestType = ContentType.MULTIPART_FORM_DATA;
		String imageType = ContentType.IMAGE_JPEG;
		String textType = ContentType.TEXT_PLAIN;

		String message = "test_message";
		Request requestSpecification = mockBasicRequest(requestType);
		MultipartBody requestBody = getBinaryTextBody(textType, message, ContentType.IMAGE_JPEG, IMAGE);
		when(requestSpecification.body()).thenReturn(requestBody);
		Triple<List<String>, List<String>, List<ReportPortalMessage>> logs = runChainComplexMessageCapture(
				requestSpecification,
				createBasicResponse(null)
		);
		assertThat(logs.getLeft(), hasSize(1));
		assertThat(logs.getMiddle(), hasSize(2));
		assertThat(logs.getRight(), hasSize(1));

		assertThat(logs.getLeft().get(0), equalTo(EMPTY_REQUEST));

		assertThat(logs.getMiddle().get(0), equalTo(Constants.BODY_PART_TAG + "\n```\n" + message + "\n```"));
		assertThat(logs.getMiddle().get(1), equalTo(EMPTY_RESPONSE));

		assertThat(
				logs.getRight().get(0).getMessage(),
				equalTo(Constants.HEADERS_TAG + Constants.LINE_DELIMITER
						+ "Content-Disposition: form-data; name=\"file\"; filename=\"pug/lucky.jpg\"" + Constants.LINE_DELIMITER
						+ Constants.LINE_DELIMITER + Constants.BODY_PART_TAG + "\n" + imageType)
		);
		assertThat(logs.getRight().get(0).getData().getMediaType(), equalTo(imageType));
		assertThat(logs.getRight().get(0).getData().read(), equalTo(image));
	}

	public static Iterable<Object[]> invalidContentTypes() {
		return Arrays.asList(
				new Object[] { "", ContentType.APPLICATION_OCTET_STREAM, ContentType.APPLICATION_OCTET_STREAM },
				new Object[] { "*/*", ContentType.APPLICATION_OCTET_STREAM, "*/*" },
				new Object[] { "something invalid", ContentType.APPLICATION_OCTET_STREAM, ContentType.APPLICATION_OCTET_STREAM },
				new Object[] { "/", ContentType.APPLICATION_OCTET_STREAM, ContentType.APPLICATION_OCTET_STREAM },
				new Object[] { "#*'\\`%^!@/\"$;", ContentType.APPLICATION_OCTET_STREAM, ContentType.APPLICATION_OCTET_STREAM },
				new Object[] { "a/a;F#%235f\\=f324$%^&", ContentType.APPLICATION_OCTET_STREAM, ContentType.APPLICATION_OCTET_STREAM }
		);
	}

	@ParameterizedTest
	@MethodSource("invalidContentTypes")
	public void test_logger_invalid_content_type(String mimeType, String expectedRequestType, String expectedResponseType)
			throws IOException {
		byte[] image = getResource(IMAGE);
		Request request = mockBasicRequest(mimeType);
		RequestBody body = mock(RequestBody.class);
		doAnswer(i -> {
			BufferedSink sink = i.getArgument(0);
			sink.write(image);
			return null;
		}).when(body).writeTo(any(BufferedSink.class));
		when(request.body()).thenReturn(body);

		ResponseBody responseBody = ResponseBody.create(image, MediaType.parse(mimeType));
		Response response = createBasicResponse(mimeType, new Headers.Builder().build(), responseBody);

		Triple<List<String>, List<String>, List<ReportPortalMessage>> logs = runChainComplexMessageCapture(request, response);
		assertThat(logs.getRight(), hasSize(2)); // Request + Response
		assertThat(logs.getRight().get(0).getMessage(), equalTo(EMPTY_REQUEST));
		assertThat(logs.getRight().get(1).getMessage(), equalTo(EMPTY_RESPONSE));

		assertThat(logs.getRight().get(0).getData().getMediaType(), equalTo(expectedRequestType));
		assertThat(logs.getRight().get(1).getData().getMediaType(), equalTo(expectedResponseType));

		assertThat(logs.getRight().get(0).getData().read(), equalTo(image));
		assertThat(logs.getRight().get(1).getData().read(), equalTo(image));
	}

	@Test
	public void test_log_filter_type() throws IOException {
		Request requestSpecification = mockBasicRequest(HTML_TYPE);
		Response responseObject = createBasicResponse(HTML_TYPE);
		Triple<List<String>, List<String>, List<ReportPortalMessage>> logs = runChainComplexMessageCapture(
				requestSpecification,
				responseObject,
				new ReportPortalOkHttp3LoggingInterceptor(LogLevel.INFO).addRequestFilter(r -> true)
		);
		assertThat(logs.getRight(), hasSize(0));
	}
}
