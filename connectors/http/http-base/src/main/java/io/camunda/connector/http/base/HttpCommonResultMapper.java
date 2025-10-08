/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base;

import static io.camunda.connector.http.client.utils.JsonHelper.isJsonStringValid;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.client.mapper.ResponseMapper;
import io.camunda.connector.http.client.mapper.StreamingHttpResponse;
import io.camunda.connector.http.client.utils.HeadersHelper;
import java.io.IOException;
import java.io.InputStream;
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
  private final ObjectMapper objectMapper;

  public HttpCommonResultMapper(
      DocumentFactory documentFactory, boolean isStoreResponseSelected, ObjectMapper objectMapper) {
    this.documentFactory = documentFactory;
    this.isStoreResponseSelected = isStoreResponseSelected;
    this.objectMapper = objectMapper;
  }

  @Override
  public HttpCommonResult apply(StreamingHttpResponse streamingHttpResponse) {
    if (isStoreResponseSelected) {
      return storeDocument(streamingHttpResponse);
    } else {
      Object body = deserializeBody(streamingHttpResponse.body());
      Map<String, Object> headers = HeadersHelper.flattenHeaders(streamingHttpResponse.headers());
      return new HttpCommonResult(
          streamingHttpResponse.status(), headers, body, streamingHttpResponse.reason(), null);
    }
  }

  private static String getContentType(Map<String, List<String>> headers) {
    return HeadersHelper.getHeaderIgnoreCase(headers, HttpHeaders.CONTENT_TYPE);
  }

  private HttpCommonResult storeDocument(StreamingHttpResponse response) {
    var headers = response.headers();
    try {
      var document =
          documentFactory.create(
              DocumentCreationRequest.from(response.body())
                  .contentType(getContentType(headers))
                  .build());
      var flattenedHeaders = HeadersHelper.flattenHeaders(headers);
      LOGGER.debug("Stored response as document. Document reference: {}", document);
      return new HttpCommonResult(
          response.status(), flattenedHeaders, null, response.reason(), document);
    } catch (Exception e) {
      LOGGER.error("Failed to create document: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to create document: " + e.getMessage(), e);
    }
  }

  /**
   * Deserializes the body from the input stream. Tries to parse the body as JSON, if it fails,
   * returns the body as a string.
   *
   * @param bodyInputStream the input stream of the response body
   * @return the deserialized body
   */
  private Object deserializeBody(InputStream bodyInputStream) {
    if (bodyInputStream == null) return null;
    else {
      try (bodyInputStream) {
        return deserializeBody(bodyInputStream.readAllBytes());
      } catch (IOException e) {
        LOGGER.error("Failed to read response body: {}", e.getMessage(), e);
        throw new RuntimeException("Failed to read response body: " + e.getMessage(), e);
      }
    }
  }

  /**
   * Extracts the body from the response content. Tries to parse the body as JSON, if it fails,
   * returns the body as a string.
   *
   * @param content the response content
   */
  private Object deserializeBody(byte[] content) throws IOException {
    String bodyString = new String(content, StandardCharsets.UTF_8);
    if (StringUtils.isNotBlank(bodyString)) {
      return isJsonStringValid(bodyString)
          ? objectMapper.readValue(bodyString, Object.class)
          : bodyString;
    }
    return null;
  }
}
