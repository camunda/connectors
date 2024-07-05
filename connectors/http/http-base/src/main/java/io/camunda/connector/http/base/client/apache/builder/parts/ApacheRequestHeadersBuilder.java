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
package io.camunda.connector.http.base.client.apache.builder.parts;

import static java.util.function.Predicate.not;
import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.apache.hc.core5.http.ContentType.MULTIPART_FORM_DATA;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

import io.camunda.connector.http.base.model.HttpCommonRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

public class ApacheRequestHeadersBuilder implements ApacheRequestPartBuilder {

  @Override
  public void build(ClassicRequestBuilder builder, HttpCommonRequest request) {
    var headers = sanitizedHeaders(request);

    var hasContentTypeHeader =
        headers.entrySet().stream().anyMatch(e -> e.getKey().equalsIgnoreCase(CONTENT_TYPE));
    if (request.getMethod().supportsBody && !hasContentTypeHeader) {
      // default content type
      builder.addHeader(CONTENT_TYPE, APPLICATION_JSON.getMimeType());
    }
    headers.entrySet().stream()
        .filter(not(defaultMultipartContentType()))
        .forEach(e -> builder.addHeader(e.getKey(), e.getValue()));
  }

  /**
   * Used to filter out the content type header if it is a multipart form data content type.
   * Otherwise, the {@link ClassicRequestBuilder} won't be able to set the boundary and will use the
   * existing header.
   *
   * <p>We should allow for custom boundary to be set in the content type header though.
   */
  private Predicate<Map.Entry<String, String>> defaultMultipartContentType() {
    return e ->
        e.getKey().equalsIgnoreCase(CONTENT_TYPE)
            && e.getValue().contains(MULTIPART_FORM_DATA.getMimeType())
            && !e.getValue().contains("boundary");
  }

  /** Remove the content type header if it is {@code null}. */
  private Map<String, String> sanitizedHeaders(HttpCommonRequest request) {
    var headers =
        Optional.ofNullable(request.getHeaders()).map(HashMap::new).orElse(new HashMap<>());
    var keysToRemove =
        headers.entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase(CONTENT_TYPE) && e.getValue() == null)
            .findFirst();
    keysToRemove.ifPresent(e -> headers.remove(e.getKey()));
    return headers;
  }
}
