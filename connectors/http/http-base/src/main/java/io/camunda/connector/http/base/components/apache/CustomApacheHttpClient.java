/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package io.camunda.connector.http.base.components.apache;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.components.HttpClient;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.request.apache.ApacheRequestFactory;
import io.camunda.connector.http.base.utils.HttpStatusHelper;
import java.io.IOException;
import java.util.Optional;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomApacheHttpClient implements HttpClient {

  public static final CustomApacheHttpClient DEFAULT =
      new CustomApacheHttpClient(createHttpClientBuilder());

  private static final Logger LOG = LoggerFactory.getLogger(CustomApacheHttpClient.class);

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
  public HttpCommonResult execute(HttpCommonRequest request, boolean remoteExecutionEnabled)
      throws Exception {
    var apacheRequest = ApacheRequestFactory.get().createHttpRequest(request);
    try {
      var result =
          httpClientBuilder
              .setDefaultRequestConfig(getRequestConfig(request))
              .build()
              .execute(apacheRequest, new HttpCommonResultResponseHandler(remoteExecutionEnabled));
      if (HttpStatusHelper.isError(result.status())) {
        throw new ConnectorException(
            String.valueOf(result.status()),
            Optional.ofNullable(result.body())
                .map(
                    body -> {
                      try {
                        return ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(
                            body);
                      } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                      }
                    })
                .orElse(result.reason()));
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
