/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import com.box.sdkgen.networking.boxnetworkclient.BoxNetworkClient;
import com.box.sdkgen.networking.fetchoptions.FetchOptions;
import com.box.sdkgen.networking.fetchresponse.FetchResponse;
import com.box.sdkgen.networking.network.NetworkSession;
import com.box.sdkgen.networking.retries.BoxRetryStrategy;
import com.box.sdkgen.networking.retries.RetryStrategy;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
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
    return create(BoxNetworkClient.getDefaultOkHttpClientBuilder());
  }

  static NetworkSession create(OkHttpClient.Builder httpClientBuilder) {
    var httpClient = httpClientBuilder.addInterceptor(BoxNetworkSession::oneShot).build();
    return new NetworkSession()
        .withNetworkClient(new BoxNetworkClient(httpClient))
        .withRetryStrategy(new StreamSafeRetryStrategy(new BoxRetryStrategy()));
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
   * Skips retries for requests streaming a body and releases the discarded response whenever a
   * retry happens or the delegate fails: the SDK keeps looping without closing binary content.
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
