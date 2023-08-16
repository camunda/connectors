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

import static io.camunda.connector.http.base.utils.Timeout.setTimeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.UrlEncodedContent;
import io.camunda.connector.http.base.auth.CustomAuthentication;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.utils.JsonHelper;
import io.camunda.connector.http.base.utils.ResponseParser;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

  private final ObjectMapper objectMapper;
  private final HttpRequestFactory requestFactory;

  public AuthenticationService(
      final ObjectMapper objectMapper, final HttpRequestFactory requestFactory) {
    this.objectMapper = objectMapper;
    this.requestFactory = requestFactory;
  }

  public String extractOAuthAccessToken(HttpResponse oauthResponse) throws IOException {
    return Optional.ofNullable(
            JsonHelper.getAsJsonElement(oauthResponse.parseAsString(), objectMapper))
        .map(jsonNode -> jsonNode.findValue(Constants.ACCESS_TOKEN).asText())
        .orElse(null);
  }

  public void fillRequestFromCustomAuthResponseData(
      final HttpCommonRequest request,
      final CustomAuthentication authentication,
      final HttpResponse httpResponse)
      throws IOException {
    String strResponse = httpResponse.parseAsString();
    Map<String, String> headers =
        ResponseParser.extractPropertiesFromBody(
            authentication.getOutputHeaders(), strResponse, objectMapper);
    if (headers != null) {
      if (!request.hasHeaders()) {
        request.setHeaders(new HashMap<>());
      }
      request.getHeaders().putAll(headers);
    }

    Map<String, String> body =
        ResponseParser.extractPropertiesFromBody(
            authentication.getOutputBody(), strResponse, objectMapper);
    if (body != null) {
      if (!request.hasBody()) {
        request.setBody(new Object());
      }
      JsonNode requestBodyAsNode = objectMapper.readTree(request.getBody().toString());
      if (requestBodyAsNode instanceof ObjectNode objectNode) {
        body.forEach(objectNode::put);
        request.setBody(objectMapper.writeValueAsString(objectNode));
      } else {
        // for now, we can add only string property to body, example of this object :
        // "{"key":"value"}" but we can expand this method
        LOGGER.error(
            "Wasn't able to append body params. Request body: {}; response: {}",
            requestBodyAsNode,
            body);
      }
    }
  }

  public com.google.api.client.http.HttpRequest createOAuthRequest(HttpCommonRequest request)
      throws IOException {
    OAuthAuthentication authentication = (OAuthAuthentication) request.getAuthentication();

    final GenericUrl genericUrl = new GenericUrl(authentication.getOauthTokenEndpoint());
    Map<String, String> data = authentication.getDataForAuthRequestBody();
    HttpContent content = new UrlEncodedContent(data);
    final String method = Constants.POST;
    final var httpRequest = requestFactory.buildRequest(method, genericUrl, content);
    httpRequest.setFollowRedirects(false);
    setTimeout(request, httpRequest);
    HttpHeaders headers = new HttpHeaders();

    if (Constants.BASIC_AUTH_HEADER.equals(authentication.getClientAuthentication())) {
      headers.setBasicAuthentication(
          authentication.getClientId(), authentication.getClientSecret());
    }
    headers.setContentType(Constants.APPLICATION_X_WWW_FORM_URLENCODED);
    httpRequest.setHeaders(headers);
    return httpRequest;
  }
}
