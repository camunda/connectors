/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
