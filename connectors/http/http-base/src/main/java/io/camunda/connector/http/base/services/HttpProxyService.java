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
package io.camunda.connector.http.base.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.AbstractHttpContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequestFactory;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.model.HttpRequestBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpProxyService {

  private static final Logger LOG = LoggerFactory.getLogger(HttpProxyService.class);

  private static final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  public static com.google.api.client.http.HttpRequest toRequestViaProxy(
      final HttpRequestFactory requestFactory,
      final HttpCommonRequest request,
      final String proxyFunctionUrl)
      throws IOException {
    // Using the JsonHttpContent cannot work with an element on the root content,
    // hence write it ourselves:
    final String contentAsJson = objectMapper.writeValueAsString(request);
    HttpContent content =
        new AbstractHttpContent(Constants.APPLICATION_JSON_CHARSET_UTF_8) {
          public void writeTo(OutputStream outputStream) throws IOException {
            outputStream.write(contentAsJson.getBytes(StandardCharsets.UTF_8));
          }
        };

    com.google.api.client.http.HttpRequest httpRequest =
        new HttpRequestBuilder()
            .method(HttpMethod.POST)
            .genericUrl(new GenericUrl(proxyFunctionUrl))
            .content(content)
            .connectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds())
            .followRedirects(false)
            .headers(HttpRequestMapper.extractRequestHeaders(request))
            .build(requestFactory);

    try {
      ProxyOAuthHelper.addOauthHeaders(
          httpRequest, ProxyOAuthHelper.initializeCredentials(proxyFunctionUrl));
      return httpRequest;
    } catch (Exception e) {
      LOG.error("Failure during OAuth authentication attempt for HTTP proxy function", e);
      // this will be visible in Operate, so should hide the internal exception
      throw new ConnectorException(
          "Failure during OAuth authentication attempt for HTTP proxy function");
    }
  }
}
