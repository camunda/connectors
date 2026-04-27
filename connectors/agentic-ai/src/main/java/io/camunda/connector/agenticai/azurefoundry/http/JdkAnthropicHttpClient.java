/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.http;

import com.anthropic.core.RequestOptions;
import com.anthropic.core.Timeout;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpRequestBody;
import com.anthropic.core.http.HttpResponse;
import com.anthropic.errors.AnthropicIoException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link com.anthropic.core.http.HttpClient} backed by the JDK {@link
 * java.net.http.HttpClient}. Exists to let the Azure AI Foundry provider reuse the agentic-ai
 * connector's existing JDK-HttpClient-based proxy support (including authenticated proxies via
 * {@code JdkHttpClientProxyConfigurator} + {@code JdkProxyAuthenticator}), instead of pulling in
 * Anthropic's bundled OkHttp-based transport.
 */
public final class JdkAnthropicHttpClient implements HttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(JdkAnthropicHttpClient.class);

  private final java.net.http.HttpClient jdkHttpClient;

  public JdkAnthropicHttpClient(java.net.http.HttpClient jdkHttpClient) {
    this.jdkHttpClient = jdkHttpClient;
  }

  @Override
  public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
    LOG.debug("Anthropic SDK request: {} {}", request.method(), request.url());
    try {
      java.net.http.HttpResponse<InputStream> jdkResponse =
          jdkHttpClient.send(toJdkRequest(request, requestOptions), BodyHandlers.ofInputStream());
      LOG.debug(
          "Anthropic SDK response: status={} url={}", jdkResponse.statusCode(), request.url());
      return toAnthropicResponse(jdkResponse);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AnthropicIoException("Interrupted while sending request", e);
    } catch (IOException e) {
      throw new AnthropicIoException("Transport error", e);
    }
  }

  @Override
  public CompletableFuture<HttpResponse> executeAsync(
      HttpRequest request, RequestOptions requestOptions) {
    LOG.debug("Anthropic SDK async request: {} {}", request.method(), request.url());
    return jdkHttpClient
        .sendAsync(toJdkRequest(request, requestOptions), BodyHandlers.ofInputStream())
        .thenApply(
            jdkResponse -> {
              LOG.debug(
                  "Anthropic SDK async response: status={} url={}",
                  jdkResponse.statusCode(),
                  request.url());
              return toAnthropicResponse(jdkResponse);
            });
  }

  @Override
  public void close() {
    // JDK HttpClient does not require explicit close; no-op.
  }

  private java.net.http.HttpRequest toJdkRequest(
      HttpRequest request, RequestOptions requestOptions) {
    java.net.http.HttpRequest.Builder builder =
        java.net.http.HttpRequest.newBuilder().uri(URI.create(request.url()));

    // Copy all request headers
    Headers headers = request.headers();
    for (String name : headers.names()) {
      for (String value : headers.values(name)) {
        builder.header(name, value);
      }
    }

    // Body
    BodyPublisher body = bodyPublisherFor(request.body());
    builder.method(request.method().name(), body);

    // Per-request timeout from RequestOptions (uses the "request" timeout dimension)
    if (requestOptions != null) {
      Timeout timeout = requestOptions.getTimeout();
      if (timeout != null) {
        Duration requestTimeout = timeout.request();
        if (requestTimeout != null) {
          builder.timeout(requestTimeout);
        }
      }
    }

    return builder.build();
  }

  private BodyPublisher bodyPublisherFor(HttpRequestBody body) {
    if (body == null) {
      return BodyPublishers.noBody();
    }
    // HttpRequestBody.writeTo() does not declare throws; it may still throw runtime exceptions
    // wrapping IO failures. ByteArrayOutputStream.write() itself never throws IOException.
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    body.writeTo(baos);
    return BodyPublishers.ofByteArray(baos.toByteArray());
  }

  private HttpResponse toAnthropicResponse(java.net.http.HttpResponse<InputStream> jdkResponse) {
    Headers.Builder hb = Headers.builder();
    jdkResponse.headers().map().forEach((name, values) -> values.forEach(v -> hb.put(name, v)));
    Headers responseHeaders = hb.build();
    int statusCode = jdkResponse.statusCode();
    InputStream responseBody = jdkResponse.body();

    return new HttpResponse() {
      @Override
      public int statusCode() {
        return statusCode;
      }

      @Override
      public Headers headers() {
        return responseHeaders;
      }

      @Override
      public InputStream body() {
        return responseBody;
      }

      @Override
      public void close() {
        try {
          responseBody.close();
        } catch (IOException e) {
          // Best-effort close; not rethrown
        }
      }
    };
  }
}
