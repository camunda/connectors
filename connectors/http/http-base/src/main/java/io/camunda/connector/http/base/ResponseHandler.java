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
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.model.HttpClientResult;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ResponseHandler.class);
  private final DocumentFactory documentFactory;
  private final boolean isStoreResponseSelected;

  public ResponseHandler(DocumentFactory documentFactory, boolean isStoreResponseSelected) {
    this.documentFactory = documentFactory;
    this.isStoreResponseSelected = isStoreResponseSelected;
  }

  public HttpCommonResult handle(HttpClientResult response) {

    try (response) { // ensure the response body stream is closed
      if (isStoreResponseSelected) {
        return storeDocument(response);
      }
      byte[] bytes = response.body().readBytes();
      Object body = extractBody(bytes);
      return new HttpCommonResult(
          response.status(), response.headers(), body, response.reason(), null);
    } catch (IOException e) {
      LOGGER.error("Failed to read response body: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to read response body: " + e.getMessage(), e);
    }
  }

  private String getContentType(Map<String, Object> headers) {
    return CustomApacheHttpClient.getHeaderIgnoreCase(headers, HttpHeaders.CONTENT_TYPE);
  }

  private HttpCommonResult storeDocument(HttpClientResult response) {
    try {
      var document =
          documentFactory
              .create(
                  DocumentCreationRequest.from(response.body().getStream())
                      .contentType(getContentType(response.headers()))
                      .build());
      LOGGER.debug("Stored response as document. Document reference: {}", document);
      return new HttpCommonResult(
          response.status(), response.headers(), null, response.reason(), document);
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
  private Object extractBody(byte[] content) throws IOException {
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
}
