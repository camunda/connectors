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
package io.camunda.connector.http.client.client.apache;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.blocklist.DefaultHttpBlocklistManager;
import io.camunda.connector.http.client.blocklist.HttpBlockListManager;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.client.apache.proxy.ProxyAwareHttpClient;
import io.camunda.connector.http.client.mapper.HttpResponse;
import io.camunda.connector.http.client.mapper.ResponseMapper;
import io.camunda.connector.http.client.mapper.StreamingHttpResponse;
import io.camunda.connector.http.client.model.HttpClientRequest;
import java.io.IOException;
import java.net.SocketTimeoutException;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.core5.http.HttpStatus;

public class CustomApacheHttpClient implements HttpClient {

  private static final String HTTP_STATUS_UNAUTHORIZED = String.valueOf(HttpStatus.SC_UNAUTHORIZED);

  private final HttpBlockListManager httpBlocklistManager = new DefaultHttpBlocklistManager();

  /**
   * Converts the given {@link HttpClientRequest} to an Apache {@link
   * org.apache.hc.core5.http.ClassicHttpRequest} and executes it.
   *
   * <p>If the request uses an OAuth2 token that was served from the cache and the server responds
   * with 401 Unauthorized, the cached token is invalidated and the request is retried once with a
   * freshly fetched token.
   *
   * @param request the request to execute
   * @return the {@link StreamingHttpResponse} containing the response details
   */
  @Override
  public <T> HttpResponse<T> execute(HttpClientRequest request, ResponseMapper<T> responseMapper) {
    // Will throw ConnectorInputException if URL is blocked
    httpBlocklistManager.validateUrlAgainstBlocklist(request.getUrl());
    var contextualizedRequest = ApacheRequestFactory.get().createHttpRequest(request);
    try {
      return executeApacheRequest(contextualizedRequest, request, responseMapper);
    } catch (ConnectorException e) {
      if (shouldRetryWithFreshToken(e, contextualizedRequest)) {
        contextualizedRequest.getRevokeTokenCallback().run();
        var freshRequest = ApacheRequestFactory.get().createHttpRequest(request);
        return executeApacheRequest(freshRequest, request, responseMapper);
      }
      throw e;
    }
  }

  private boolean shouldRetryWithFreshToken(
      ConnectorException e, ContextualizedClassicHttpRequest contextualizedRequest) {
    return HTTP_STATUS_UNAUTHORIZED.equals(e.getErrorCode())
        && contextualizedRequest.wasTokenCached()
        && contextualizedRequest.getRevokeTokenCallback() != null;
  }

  private <T> HttpResponse<T> executeApacheRequest(
      ContextualizedClassicHttpRequest contextualizedRequest,
      HttpClientRequest request,
      ResponseMapper<T> responseMapper) {
    var apacheRequest = contextualizedRequest.getRequest();
    var host = apacheRequest.getAuthority().getHostName();
    var scheme = apacheRequest.getScheme();

    try (var client =
        new ProxyAwareHttpClient(
            new ProxyAwareHttpClient.TimeoutConfiguration(
                request.getConnectionTimeoutInSeconds(), request.getReadTimeoutInSeconds()),
            new ProxyAwareHttpClient.ProxyContext(scheme, host))) {

      var apacheResponseHandler = new CustomResponseHandler<>(responseMapper);
      return client.execute(apacheRequest, apacheResponseHandler);
    } catch (ClientProtocolException e) {
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_SERVER_ERROR),
          "An error with the HTTP protocol occurred",
          e);
    } catch (SocketTimeoutException e) {
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_REQUEST_TIMEOUT),
          "The request timed out. Please try increasing the read and connection timeouts.",
          e);
    } catch (IOException e) {
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_REQUEST_TIMEOUT),
          "An error occurred while executing the request, or the connection was aborted",
          e);
    }
  }
}
