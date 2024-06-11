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
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicNameValuePair;

/**
 * Maps the request body of a {@link HttpCommonRequest} to an Apache {@link ClassicRequestBuilder}.
 */
public class ApacheRequestBodyBuilder implements ApacheRequestPartBuilder {
  public static final String EMPTY_BODY = "";

  @Override
  public void build(ClassicRequestBuilder builder, HttpCommonRequest request) {
    if (request.getMethod().supportsBody) {
      if (!request.hasBody()) {
        /**
         * We need to set the body to something not null due to how {@link
         * io.camunda.connector.http.base.components.apache.CustomApacheHttpClient}{@link
         * #build(ClassicRequestBuilder, HttpCommonRequest)} works. If the body is null, the {@link
         * ClassicRequestBuilder} will override it in some cases (PUT/POST using query parameters).
         */
        builder.setEntity(EMPTY_BODY);
        return;
      }

      if (request.getBody() instanceof Map<?, ?> body) {
        if (isFormUrlEncoded(request)) {
          setUrlEncodedFormEntity(body, builder);
        } else {
          setStringEntity(builder, request);
        }
      } else {
        setStringEntity(builder, request);
      }
    }
  }

  private Optional<String> tryGetContentType(HttpCommonRequest request) {
    return Optional.ofNullable(request.getHeaders()).map(headers -> headers.get(CONTENT_TYPE));
  }

  private boolean isFormUrlEncoded(HttpCommonRequest request) {
    return tryGetContentType(request)
        .map(
            s ->
                ContentType.parse(s)
                    .getMimeType()
                    .equalsIgnoreCase(APPLICATION_FORM_URLENCODED.getMimeType()))
        .orElse(false);
  }

  private void setStringEntity(ClassicRequestBuilder requestBuilder, HttpCommonRequest request) {
    Object body = request.getBody();
    Optional<String> contentType = tryGetContentType(request);
    try {
      requestBuilder.setEntity(
          body instanceof String s
              ? new StringEntity(
                  s,
                  contentType
                      .map(ContentType::parse)
                      .orElse(ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8)))
              : new StringEntity(
                  ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(body),
                  contentType
                      .map(ContentType::parse)
                      .orElse(ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8))));
    } catch (JsonProcessingException e) {
      throw new ConnectorException("Failed to serialize request body:" + body, e);
    }
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
}
