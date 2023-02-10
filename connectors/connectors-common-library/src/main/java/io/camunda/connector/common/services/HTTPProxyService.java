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
package io.camunda.connector.common.services;

import com.google.api.client.http.AbstractHttpContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.gson.Gson;
import io.camunda.connector.common.constants.Constants;
import io.camunda.connector.common.model.CommonRequest;
import io.camunda.connector.common.model.HttpRequestBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class HTTPProxyService {

  public static HttpRequest toRequestViaProxy(
      final Gson gson,
      final HttpRequestFactory requestFactory,
      final CommonRequest request,
      final String proxyFunctionUrl)
      throws IOException {
    // Using the JsonHttpContent cannot work with an element on the root content,
    // hence write it ourselves:
    final String contentAsJson = gson.toJson(request);
    HttpContent content =
        new AbstractHttpContent(Constants.APPLICATION_JSON_CHARSET_UTF_8) {
          public void writeTo(OutputStream outputStream) throws IOException {
            outputStream.write(contentAsJson.getBytes(StandardCharsets.UTF_8));
          }
        };

    HttpRequest httpRequest =
        new HttpRequestBuilder()
            .method(Constants.POST)
            .genericUrl(new GenericUrl(proxyFunctionUrl))
            .content(content)
            .connectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds())
            .followRedirects(false)
            .build(requestFactory);

    ProxyOAuthHelper.addOauthHeaders(
        httpRequest, ProxyOAuthHelper.initializeCredentials(proxyFunctionUrl));
    return httpRequest;
  }
}
