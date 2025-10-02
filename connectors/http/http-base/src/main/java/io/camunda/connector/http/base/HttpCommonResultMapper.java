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
package io.camunda.connector.http.base;

import static io.camunda.connector.http.client.utils.JsonHelper.isJsonStringValid;

import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.client.client.ResponseMapper;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.model.response.StreamingHttpResponse;
import io.camunda.connector.http.client.utils.HeadersHelper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps a {@link StreamingHttpResponse} to a {@link HttpCommonResult}. If the option to store the
 * response as a document is selected, the response body is stored as a document using the provided
 * {@link DocumentFactory}.
 */
public class HttpCommonResultMapper implements ResponseMapper<HttpCommonResult> {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpCommonResultMapper.class);
  private final DocumentFactory documentFactory;
  private final boolean isStoreResponseSelected;

  public HttpCommonResultMapper(DocumentFactory documentFactory,
      boolean isStoreResponseSelected) {
    this.documentFactory = documentFactory;
    this.isStoreResponseSelected = isStoreResponseSelected;
  }

  @Override
  public HttpCommonResult apply(StreamingHttpResponse streamingHttpResponse) {
    if (isStoreResponseSelected) {
      return storeDocument(streamingHttpResponse);
    } else {
      try { // stream is closed by the http client
        byte[] bytes = streamingHttpResponse.body() != null
            ? streamingHttpResponse.body().readAllBytes()
            : null;
        Object body = deserializeBody(bytes);
        Map<String, Object> headers = parseHeaders(streamingHttpResponse.headers());
        return new HttpCommonResult(
            streamingHttpResponse.status(),
            headers,
            body,
            streamingHttpResponse.reason(),
            null);
      } catch (IOException e) {
        LOGGER.error("Failed to read response body: {}", e.getMessage(), e);
        throw new RuntimeException("Failed to read response body: " + e.getMessage(), e);
      }
    }
  }

  private String getContentType(Map<String, List<String>> headers) {
    return HeadersHelper.getHeaderIgnoreCase(headers, HttpHeaders.CONTENT_TYPE);
  }

  private HttpCommonResult storeDocument(StreamingHttpResponse response) {
    var headers = response.headers();
    try {
      var document =
          documentFactory
              .create(
                  DocumentCreationRequest.from(response.body())
                      .contentType(getContentType(headers))
                      .build());
      var formattedHeaders = parseHeaders(headers);
      LOGGER.debug("Stored response as document. Document reference: {}", document);
      return new HttpCommonResult(
          response.status(), formattedHeaders, null, response.reason(), document);
    } catch (Exception e) {
      LOGGER.error("Failed to create document: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to create document: " + e.getMessage(), e);
    }
  }

  /**
   * Extracts the body from the response content. Tries to parse the body as JSON, if it fails,
   * returns the body as a string.
   *
   * @param content the response content
   */
  private Object deserializeBody(byte[] content) throws IOException {
    String bodyString = null;
    if (content != null) {
      bodyString = new String(content, StandardCharsets.UTF_8);
    }

    if (StringUtils.isNotBlank(bodyString)) {
      return isJsonStringValid(bodyString)
          ? ConnectorsObjectMapperSupplier.getCopy().readValue(bodyString, Object.class)
          : bodyString;
    }
    return null;
  }

  private Map<String, Object> parseHeaders(Map<String, List<String>> headers) {
    // convert single value headers from List<String> to String
    return headers.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                  List<String> values = entry.getValue();
                  if (values.size() == 1) {
                    return values.getFirst();
                  } else {
                    return values;
                  }
                }));
  }
}
