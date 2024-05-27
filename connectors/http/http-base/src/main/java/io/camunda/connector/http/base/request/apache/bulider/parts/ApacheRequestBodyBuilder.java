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
package io.camunda.connector.http.base.request.apache.bulider.parts;

import static org.apache.hc.core5.http.ContentType.APPLICATION_FORM_URLENCODED;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicNameValuePair;

/**
 * Maps the request body of a {@link HttpCommonRequest} to an Apache {@link ClassicRequestBuilder}.
 */
public class ApacheRequestBodyBuilder implements ApacheRequestPartBuilder {

  @Override
  public void build(ClassicRequestBuilder builder, HttpCommonRequest request)
      throws JsonProcessingException {
    if (request.getMethod().supportsBody && request.hasBody()) {
      unescapeBody(request);

      switch (request.getBody()) {
        case Map<?, ?> body -> {
          if (isFormUrlEncoded(request)) {
            setUrlEncodedFormEntity(body, builder);
          } else {
            setStringEntity(builder, request.getBody());
          }
        }
        case List<?> body -> setStringEntity(builder, body);
        case String body -> setStringEntity(builder, body);
        default -> throw new IllegalStateException(
            "Unexpected value type for the request body. Excepting Map or String, got "
                + request.getBody().getClass().getSimpleName());
      }
    }
  }

  private boolean isFormUrlEncoded(HttpCommonRequest request) {
    return request.hasHeaders()
        && Optional.ofNullable(request.getHeaders().get(CONTENT_TYPE))
            .map(s -> s.equalsIgnoreCase(APPLICATION_FORM_URLENCODED.getMimeType()))
            .orElse(false);
  }

  private void setStringEntity(ClassicRequestBuilder requestBuilder, Object body)
      throws JsonProcessingException {
    requestBuilder.setEntity(
        body instanceof String s
            ? new StringEntity(s)
            : new StringEntity(
                ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(body)));
  }

  private void setUrlEncodedFormEntity(Map<?, ?> body, ClassicRequestBuilder requestBuilder) {
    requestBuilder.setEntity(
        HttpEntities.createUrlEncoded(
            body.entrySet().stream()
                .map(
                    e ->
                        new BasicNameValuePair(
                            String.valueOf(e.getKey()), String.valueOf(e.getValue())))
                .collect(Collectors.toList()),
            StandardCharsets.UTF_8));
  }

  private void unescapeBody(HttpCommonRequest request) {
    if (request.getBody() instanceof String) {
      String unescapedBody = StringEscapeUtils.unescapeJson((String) request.getBody());
      request.setBody(unescapedBody);
    }
  }
}
