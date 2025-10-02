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
import io.camunda.connector.http.client.client.ResponseMapper;
import io.camunda.connector.http.client.client.apache.proxy.ProxyAwareHttpClient;
import io.camunda.connector.http.client.exception.ConnectorExceptionMapper;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.response.StreamingHttpResponse;
import io.camunda.connector.http.client.utils.HttpStatusHelper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.core5.http.HttpStatus;

public class CustomApacheHttpClient implements HttpClient {

  private final HttpBlockListManager httpBlocklistManager = new DefaultHttpBlocklistManager();

  @Override
  public <T> T execute(HttpClientRequest request, ResponseMapper<T> responseMapper) {

    try (var result = execute(request)) {
      return responseMapper.apply(result);
    } catch (Exception e) {
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR),
          "An error occurred while processing the response",
          e);
    }
  }

  /**
   * Converts the given {@link HttpClientRequest} to an Apache {@link
   * org.apache.hc.core5.http.ClassicHttpRequest} and executes it.
   *
   * <p>This method is thread-safe.
   *
   * @param request the request to execute
   * @return the {@link StreamingHttpResponse} containing the response details
   */
  @Override
  public StreamingHttpResponse execute(HttpClientRequest request) {
    // Will throw ConnectorInputException if URL is blocked
    httpBlocklistManager.validateUrlAgainstBlocklist(request.getUrl());

    try {
      var apacheRequest = ApacheRequestFactory.get().createHttpRequest(request);
      var host = apacheRequest.getAuthority().getHostName();
      var scheme = apacheRequest.getScheme();
      var client =
          new ProxyAwareHttpClient(
              new ProxyAwareHttpClient.TimeoutConfiguration(
                  request.getConnectionTimeoutInSeconds(), request.getReadTimeoutInSeconds()),
              new ProxyAwareHttpClient.ProxyContext(scheme, host));
      var result = client.execute(
          apacheRequest,
          new HttpCommonResultResponseHandler(
              () ->
              {
                try {
                  client.close();
                } catch (IOException e) {
                  throw new RuntimeException("Failed to reset the request", e);
                }
              }));
      if (HttpStatusHelper.isError(result.status())) {
        throw ConnectorExceptionMapper.from(result);
      }
      return result;
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
