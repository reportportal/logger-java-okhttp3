# Report Portal logger for OkHttp3 client

[![Maven Central](https://img.shields.io/maven-central/v/com.epam.reportportal/logger-java-okhttp3.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.epam.reportportal/logger-java-okhttp3)
[![CI Build](https://github.com/reportportal/logger-java-okhttp3/actions/workflows/ci.yml/badge.svg)](https://github.com/reportportal/logger-java-okhttp3/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/reportportal/logger-java-okhttp3/branch/develop/graph/badge.svg?token=M2J13Z075Y)](https://codecov.io/gh/reportportal/logger-java-okhttp3)
[![Join Slack chat!](https://slack.epmrpp.reportportal.io/badge.svg)](https://slack.epmrpp.reportportal.io/)
[![stackoverflow](https://img.shields.io/badge/reportportal-stackoverflow-orange.svg?style=flat)](http://stackoverflow.com/questions/tagged/reportportal)
[![Build with Love](https://img.shields.io/badge/build%20with-❤%EF%B8%8F%E2%80%8D-lightgrey.svg)](http://reportportal.io?style=flat)

The latest version: 5.1.1. Please use `Maven Central` link above to get the logger.

## Overview

OkHttp3 Request/Response logger for Report Portal

The logger intercept and logs all Requests and Responses issued by OkHttp into Report Portal in Markdown format,
including multipart requests. It recognizes payload types and attach them in corresponding manner: image types will be
logged as images with thumbnails, binary types will be logged as entry attachments, text types will be formatted and
logged in Markdown code blocks.

## Configuration

### Build system configuration

You need to add the logger as one of your dependencies in Maven or Gradle.

#### Maven

`pom.xml`

```xml

<project>
    <!-- project declaration omitted -->

    <dependencies>
        <dependency>
            <groupId>com.epam.reportportal</groupId>
            <artifactId>logger-java-okhttp3</artifactId>
            <version>5.1.1</version>
        </dependency>
    </dependencies>

    <!-- build config omitted -->
</project>
```

#### Gradle

`build.gradle`

```groovy
dependencies {
    testCompile 'com.epam.reportportal:logger-java-okhttp3:5.1.1'
}
```

### OkHttp configuration

To start getting Request and Response logging in Report Portal you need to add the logger as one of your OkHttp
interceptors. The best place for it is one before hook methods. E.G. `@BeforeClass` method for TestNG or `@BeforeAll`
method for JUnit 5:

```java
public class BaseTest {
	private OkHttpClient client;

	@BeforeClass
	public void setupOkHttp3() {
		client = new OkHttpClient.Builder().addInterceptor(new ReportPortalOkHttp3LoggingInterceptor(LogLevel.INFO))
				.build();
	}
}
```

### Sanitize Request / Response data

To avoid logging sensitive data into Report Portal you can use corresponding converters:

* Cookie converter
* Header converter
* URI converter
* Content prettiers

Cookie, Header and URI converters are set in the logger constructor:

```java
public class BaseTest {
	private OkHttpClient client;

	@BeforeClass
	public void setupOkHttp3() {
		client = new OkHttpClient.Builder().addInterceptor(new ReportPortalOkHttp3LoggingInterceptor(LogLevel.INFO,
						SanitizingHttpHeaderConverter.INSTANCE,
						DefaultHttpHeaderConverter.INSTANCE
				))
				.authenticator((route, response) -> {
					String credential = "Bearer test_token";
					return response.request().newBuilder().header("Authorization", credential).build();
				})
				.followRedirects(true)
				.build();
	}
}
```

You are free to implement any converter by yourself with `java.util.function.Function` interface.

Content prettier are more complex, they parse data based on its content type and apply defined transformations. Default
prettiers just pretty-print JSON, HTML and XML data. To apply a custom content prettier call
`ReportPortalOkHttp3LoggingInterceptor.setContentPrettiers`.
E.G.:

```java
public class BaseTest {
	private static final Map<String, Function<String, String>> MY_PRETTIERS = new HashMap<String, Function<String, String>>() {{
		put(ContentType.APPLICATION_XML.getMimeType(), XmlPrettier.INSTANCE);
		put(ContentType.APPLICATION_SOAP_XML.getMimeType(), XmlPrettier.INSTANCE);
		put(ContentType.APPLICATION_ATOM_XML.getMimeType(), XmlPrettier.INSTANCE);
		put(ContentType.APPLICATION_SVG_XML.getMimeType(), XmlPrettier.INSTANCE);
		put(ContentType.APPLICATION_XHTML_XML.getMimeType(), XmlPrettier.INSTANCE);
		put(ContentType.TEXT_XML.getMimeType(), XmlPrettier.INSTANCE);
		put(ContentType.APPLICATION_JSON.getMimeType(), JsonPrettier.INSTANCE);
		put("text/json", JsonPrettier.INSTANCE);
		put(ContentType.TEXT_HTML.getMimeType(), HtmlPrettier.INSTANCE);
	}};

	private OkHttpClient client;

	@BeforeClass
	public void setupOkHttp3() {
		client = new OkHttpClient.Builder().addInterceptor(new ReportPortalOkHttp3LoggingInterceptor(LogLevel.INFO).setContentPrettiers(
				MY_PRETTIERS)).build();
	}
}
```
