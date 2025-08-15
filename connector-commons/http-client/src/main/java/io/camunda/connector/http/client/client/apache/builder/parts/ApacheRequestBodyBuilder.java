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
package io.camunda.connector.http.client.client.apache.builder.parts;

import static org.apache.hc.core5.http.ContentType.MULTIPART_FORM_DATA;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.utils.DocumentHelper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps the request body of a {@link HttpClientRequest} to an Apache {@link ClassicRequestBuilder}.
 */
public class ApacheRequestBodyBuilder implements ApacheRequestPartBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(ApacheRequestBodyBuilder.class);

  public static final String EMPTY_BODY = "";
  public static final ObjectMapper mapperIgnoreNull =
      ConnectorsObjectMapperSupplier.getCopy()
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  public static final ObjectMapper mapperSendNull = ConnectorsObjectMapperSupplier.getCopy();

  @Override
  public void build(ClassicRequestBuilder builder, HttpClientRequest request) {
    if (request.getMethod().supportsBody) {
      if (!request.hasBody()) {
        /**
         * We need to set the body to something not null due to how {@link
         * CustomApacheHttpClient}{@link #build(ClassicRequestBuilder, HttpClientRequest)} works. If
         * the body is null, the {@link ClassicRequestBuilder} will override it in some cases
         * (PUT/POST using query parameters).
         */
        builder.setEntity(EMPTY_BODY);
        return;
      }

      if (request.getBody() instanceof Map<?, ?> body) {
        tryGetContentType(request)
            .ifPresentOrElse(
                contentType ->
                    builder.setEntity(createEntityForContentType(contentType, body, request)),
                () -> builder.setEntity(createStringEntity(request)));
      } else {
        builder.setEntity(createStringEntity(request));
      }
    }
  }

  private HttpEntity createEntityForContentType(
      ContentType contentType, Map<?, ?> body, HttpClientRequest request) {
    HttpEntity entity;
    if (contentType.getMimeType().equalsIgnoreCase(MULTIPART_FORM_DATA.getMimeType())) {
      entity = new DocumentAwareMultipartEntityBuilder(body, contentType).build();
    } else if (contentType
        .getMimeType()
        .equalsIgnoreCase(ContentType.APPLICATION_FORM_URLENCODED.getMimeType())) {
      entity = createUrlEncodedFormEntity(body);
    } else {
      entity = createStringEntity(request);
    }
    return entity;
  }

  private Optional<ContentType> tryGetContentType(HttpClientRequest request) {
    return request.getHeader(CONTENT_TYPE).map(ContentType::parse);
  }

  private HttpEntity createStringEntity(HttpClientRequest request) {
    Object body = request.getBody();
    if (body instanceof Map map) {
      body = new DocumentHelper().parseDocumentsInBody(map, Document::asByteArray);
    }
    Optional<ContentType> contentType = tryGetContentType(request);
    try {
      return body instanceof String s
          ? new StringEntity(
              s, contentType.orElse(ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8)))
          : new StringEntity(
              request.isIgnoreNullValues()
                  ? mapperIgnoreNull.writeValueAsString(body)
                  : mapperSendNull.writeValueAsString(body),
              contentType.orElse(ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));
    } catch (JsonProcessingException e) {
      throw new ConnectorException("Failed to serialize request body:" + body, e);
    }
  }

  private HttpEntity createUrlEncodedFormEntity(Map<?, ?> body) {
    return HttpEntities.createUrlEncoded(
        body.entrySet().stream()
            .map(
                e ->
                    new BasicNameValuePair(
                        String.valueOf(e.getKey()),
                        Optional.ofNullable(e.getValue()).map(String::valueOf).orElse(null)))
            .collect(Collectors.toList()),
        StandardCharsets.UTF_8);
  }
}
