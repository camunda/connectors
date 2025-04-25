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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.net.ssl.SSLSession;

public class DummyHttpResponse<T> implements HttpResponse<T> {
  private final int statusCode;
  private final Map<String, String> headers;
  private final T body;
  private final String uri;

  public DummyHttpResponse(int statusCode, Map<String, String> headers, T body, String uri) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.body = body;
    this.uri = uri;
  }

  @Override
  public int statusCode() {
    return statusCode;
  }

  @Override
  public HttpRequest request() {
    return null;
  }

  @Override
  public Optional<HttpResponse<T>> previousResponse() {
    return Optional.empty();
  }

  @Override
  public HttpHeaders headers() {
    return HttpHeaders.of(
        headers.entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), List.of(entry.getValue())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
        (s, s2) -> true);
  }

  @Override
  public T body() {
    return body;
  }

  @Override
  public Optional<SSLSession> sslSession() {
    return Optional.empty();
  }

  @Override
  public URI uri() {
    return URI.create(uri);
  }

  @Override
  public HttpClient.Version version() {
    return null;
  }

  public record Person(String name, int age) {}
}
