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
package io.camunda.connector.runtime.core.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class InstanceForwardingHttpClient {
  private final ObjectMapper objectMapper;
  public static final String X_CAMUNDA_FORWARDED_FOR = "X-Camunda-Forwarded-For";
  private final HttpClient httpClient;
  private final InstancesUrlBuilder urlBuilder;

  public InstanceForwardingHttpClient(InstancesUrlBuilder urlBuilder, ObjectMapper objectMapper) {
    this(HttpClient.newHttpClient(), urlBuilder, objectMapper);
  }

  public InstanceForwardingHttpClient(
      HttpClient httpClient, InstancesUrlBuilder urlBuilder, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.urlBuilder = urlBuilder;
    this.objectMapper = objectMapper;
  }

  public <T> List<T> execute(
      String method,
      String path,
      String body,
      Map<String, String> headers,
      TypeReference<T> responseType,
      String hostname)
      throws IOException, InterruptedException {
    List<T> responses = new ArrayList<>();
    var urls = urlBuilder.buildUrls(path);

    for (String urlPath : urls) {
      var requestBuilder = HttpRequest.newBuilder(URI.create(urlPath));
      if (StringUtils.isNotBlank(body)) {
        requestBuilder.method(
            method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
      } else {
        requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
      }
      if (headers != null) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          try {
            requestBuilder.header(entry.getKey(), entry.getValue());
          } catch (IllegalArgumentException e) {
            // Ignore invalid headers
          }
        }
      }
      requestBuilder.header(X_CAMUNDA_FORWARDED_FOR, hostname);
      HttpResponse<String> response =
          httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 400 && response.body() != null) {
        responses.add(objectMapper.readValue(response.body(), responseType));
      }
    }
    return responses;
  }
}
