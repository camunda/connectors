/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.client.apache;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.base.client.HttpClient;
import io.camunda.connector.http.base.client.HttpStatusHelper;
import io.camunda.connector.http.base.exception.HttpCommonResultException;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import java.io.IOException;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;

public class CustomApacheHttpClient implements HttpClient {

  public static final CustomApacheHttpClient DEFAULT =
      new CustomApacheHttpClient(createHttpClientBuilder());

  private final HttpClientBuilder httpClientBuilder;

  private CustomApacheHttpClient(HttpClientBuilder httpClientBuilder) {
    this.httpClientBuilder = httpClientBuilder;
  }

  /**
   * Returns the default instance of {@link CustomApacheHttpClient}. The default instance is
   * configured with a {@link HttpClientBuilder} that has a {@link
   * PoolingHttpClientConnectionManager} with a maximum of {@link Integer#MAX_VALUE} connections.
   *
   * @return the default instance of {@link CustomApacheHttpClient}
   */
  public static CustomApacheHttpClient getDefault() {
    return DEFAULT;
  }

  /**
   * Creates a new instance of {@link CustomApacheHttpClient} with the given {@link
   * HttpClientBuilder}. Use this method if you want to customize the {@link HttpClientBuilder}. See
   * {@link #getDefault()} for the default instance.
   *
   * @param httpClientBuilder the {@link HttpClientBuilder} to use
   * @return a new instance of {@link CustomApacheHttpClient}
   * @see HttpClients#custom()
   */
  public static CustomApacheHttpClient create(HttpClientBuilder httpClientBuilder) {
    return new CustomApacheHttpClient(httpClientBuilder);
  }

  private static HttpClientBuilder createHttpClientBuilder() {
    return HttpClients.custom()
        .setConnectionManager(createConnectionManager())
        .disableRedirectHandling();
  }

  private static PoolingHttpClientConnectionManager createConnectionManager() {
    PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    connectionManager.setMaxTotal(Integer.MAX_VALUE);
    connectionManager.setDefaultMaxPerRoute(Integer.MAX_VALUE);

    // Socket config
    connectionManager.setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build());

    return connectionManager;
  }

  /**
   * Converts the given {@link HttpCommonRequest} to an Apache {@link
   * org.apache.hc.core5.http.ClassicHttpRequest} and executes it.
   *
   * @param request the request to execute
   * @param remoteExecutionEnabled whether to use the internal Google Function to execute the
   *     request remotely
   * @return the {@link HttpCommonResult}
   */
  @Override
  public HttpCommonResult execute(HttpCommonRequest request, boolean remoteExecutionEnabled) {
    var apacheRequest = ApacheRequestFactory.get().createHttpRequest(request);
    try {
      var result =
          httpClientBuilder
              .setDefaultRequestConfig(getRequestConfig(request))
              .build()
              .execute(apacheRequest, new HttpCommonResultResponseHandler(remoteExecutionEnabled));
      if (HttpStatusHelper.isError(result.status())) {
        throw new HttpCommonResultException(result);
      }
      return result;
    } catch (ClientProtocolException e) {
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_SERVER_ERROR),
          "An error with the HTTP protocol occurred",
          e);
    } catch (IOException e) {
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_REQUEST_TIMEOUT),
          "An error occurred while executing the request, or the connection was aborted",
          e);
    }
  }

  private RequestConfig getRequestConfig(HttpCommonRequest request) {
    return RequestConfig.custom()
        .setConnectionRequestTimeout(Timeout.ofSeconds(request.getConnectionTimeoutInSeconds()))
        .setResponseTimeout(Timeout.ofSeconds(request.getReadTimeoutInSeconds()))
        .build();
  }
}
