/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import com.box.sdkgen.box.developertokenauth.BoxDeveloperTokenAuth;
import com.box.sdkgen.networking.boxnetworkclient.BoxNetworkClient;
import com.box.sdkgen.networking.fetchoptions.FetchOptions;
import com.box.sdkgen.networking.fetchresponse.FetchResponse;
import com.box.sdkgen.networking.network.NetworkSession;
import com.box.sdkgen.networking.retries.BoxRetryStrategy;
import com.box.sdkgen.networking.retries.RetryStrategy;
import io.camunda.connector.http.client.client.jdk.proxy.JdkProxySelector;
import io.camunda.connector.http.client.proxy.EnvironmentProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration.ProxyDetails;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;

/**
 * Network session shared by all executions, so the OkHttp connection pool is reused.
 *
 * <p>Requests that stream a document are never resent, neither by the SDK nor by OkHttp, because
 * the stream is consumed by the first attempt. Every other request keeps the SDK retry policy.
 */
final class BoxNetworkSession {

  private BoxNetworkSession() {}

  static NetworkSession create() {
    return create(
        BoxNetworkClient.getDefaultOkHttpClientBuilder(),
        EnvironmentProxyConfiguration.withDefaults());
  }

  static NetworkSession create(OkHttpClient.Builder httpClientBuilder) {
    return create(httpClientBuilder, ProxyConfiguration.NONE);
  }

  /**
   * The Box SDK is bound to OkHttp, so the connector HTTP client cannot be reused. Its proxy
   * configuration (CONNECTOR_HTTP(S)_PROXY_* and non-proxy hosts) is applied to OkHttp instead.
   */
  static NetworkSession create(
      OkHttpClient.Builder httpClientBuilder, ProxyConfiguration proxyConfiguration) {
    var httpClient =
        httpClientBuilder
            .proxySelector(new JdkProxySelector(proxyConfiguration))
            .proxyAuthenticator(proxyAuthenticator(proxyConfiguration))
            .addInterceptor(BoxNetworkSession::oneShot)
            .build();
    var defaults = new NetworkSession();
    // a custom client replaces the SDK's own, so the session's default timeouts must be reapplied
    var networkClient =
        new BoxNetworkClient(httpClient).withTimeoutConfig(defaults.getTimeoutConfig());
    return defaults
        .withNetworkClient(networkClient)
        .withRetryStrategy(new StreamSafeRetryStrategy(new BoxRetryStrategy()));
  }

  private static Authenticator proxyAuthenticator(ProxyConfiguration proxyConfiguration) {
    return (route, response) -> {
      Request request = response.request();
      // a second 407 means the credentials were rejected, do not loop
      if (request.header("Proxy-Authorization") != null) {
        return null;
      }
      return proxyConfiguration
          .getProxyDetails(request.url().scheme())
          .filter(ProxyDetails::hasCredentials)
          .map(
              details ->
                  request
                      .newBuilder()
                      .header(
                          "Proxy-Authorization",
                          Credentials.basic(details.user(), details.password()))
                      .build())
          .orElse(null);
    };
  }

  // OkHttp resends e.g. a 503 with "Retry-After: 0" on its own unless the body is one-shot.
  private static okhttp3.Response oneShot(okhttp3.Interceptor.Chain chain) throws IOException {
    var request = chain.request();
    if (request.body() instanceof MultipartBody body) {
      request = request.newBuilder().method(request.method(), new OneShotBody(body)).build();
    }
    return chain.proceed(request);
  }

  private static final class OneShotBody extends RequestBody {
    private final RequestBody delegate;

    OneShotBody(RequestBody delegate) {
      this.delegate = delegate;
    }

    @Override
    public MediaType contentType() {
      return delegate.contentType();
    }

    @Override
    public long contentLength() throws IOException {
      return delegate.contentLength();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
      delegate.writeTo(sink);
    }

    @Override
    public boolean isOneShot() {
      return true;
    }
  }

  /**
   * Skips retries for requests streaming a body or rejected with a 401 under a developer token, and
   * releases the discarded response whenever a retry happens or the delegate fails: the SDK keeps
   * looping without closing binary content.
   */
  static final class StreamSafeRetryStrategy implements RetryStrategy {
    private final RetryStrategy delegate;

    StreamSafeRetryStrategy(RetryStrategy delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean shouldRetry(FetchOptions options, FetchResponse response, int attempt) {
      if (options.fileStream != null || options.multipartData != null) {
        return false;
      }
      // a developer token cannot be refreshed, retrying would only hide the 401 behind an SDK error
      if (response.getStatus() == 401 && options.getAuth() instanceof BoxDeveloperTokenAuth) {
        return false;
      }
      boolean retry;
      try {
        retry = delegate.shouldRetry(options, response, attempt);
      } catch (RuntimeException e) {
        close(response);
        throw e;
      }
      if (retry) {
        close(response);
      }
      return retry;
    }

    @Override
    public double retryAfter(FetchOptions options, FetchResponse response, int attempt) {
      return delegate.retryAfter(options, response, attempt);
    }

    private static void close(FetchResponse response) {
      InputStream content = response.content;
      if (content == null) {
        return;
      }
      try {
        content.close();
      } catch (IOException ignored) {
        // releasing the discarded response must not mask the retry decision
      }
    }
  }
}
