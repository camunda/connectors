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
package io.camunda.connector.http.base.client.apache;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.base.ExecutionEnvironment;
import io.camunda.connector.http.base.client.HttpClient;
import io.camunda.connector.http.base.client.HttpStatusHelper;
import io.camunda.connector.http.base.client.apache.proxy.ProxyAwareHttpClient;
import io.camunda.connector.http.base.client.apache.proxy.ProxyHandler;
import io.camunda.connector.http.base.exception.ConnectorExceptionMapper;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import java.io.IOException;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.core5.http.HttpStatus;

public class CustomApacheHttpClient implements HttpClient {

  /**
   * Converts the given {@link HttpCommonRequest} to an Apache {@link
   * org.apache.hc.core5.http.ClassicHttpRequest} and executes it.
   *
   * @param request the request to execute
   * @param executionEnvironment the {@link ExecutionEnvironment} we are in
   * @return the {@link HttpCommonResult}
   */
  @Override
  public HttpCommonResult execute(
      HttpCommonRequest request,
      ProxyHandler proxyHandler,
      @Nullable ExecutionEnvironment executionEnvironment) {
    try {
      var apacheRequest = ApacheRequestFactory.get().createHttpRequest(request);
      var host = apacheRequest.getAuthority().getHostName();
      var scheme = apacheRequest.getScheme();
      try (var client =
          new ProxyAwareHttpClient(
              new ProxyAwareHttpClient.TimeoutConfiguration(
                  request.getConnectionTimeoutInSeconds(), request.getReadTimeoutInSeconds()),
              new ProxyAwareHttpClient.ProxyContext(scheme, host))) {
        var result =
            client.execute(
                apacheRequest,
                new HttpCommonResultResponseHandler(
                    executionEnvironment, request.isStoreResponse()));
        if (HttpStatusHelper.isError(result.status())) {
          throw ConnectorExceptionMapper.from(result);
        }
        return result;
      }
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
}
