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
package io.camunda.connector.http.client.document;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.http.client.ExecutionEnvironment;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.model.HttpClientResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileResponseHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileResponseHandler.class);
  private final ExecutionEnvironment executionEnvironment;
  private final boolean isStoreResponseSelected;

  public FileResponseHandler(
      @Nullable ExecutionEnvironment executionEnvironment, boolean isStoreResponseSelected) {
    this.executionEnvironment = executionEnvironment;
    this.isStoreResponseSelected = isStoreResponseSelected;
  }

  public Document handleCloudFunctionResult(HttpClientResult result)
      throws DocumentCreationException {
    if (!storeResponseSelected()) return null;

    var body = result.body();
    if (body instanceof String stringBody) {
      LOGGER.debug("Storing document from Cloud Function Result body");
      return handle(
          result.headers(),
          Base64.getDecoder().decode(stringBody.getBytes(StandardCharsets.UTF_8)));
    }
    LOGGER.warn("Cannot store document from body of type {} (expected String)", body.getClass());
    return null;
  }

  public Document handle(Map<String, Object> headers, byte[] content)
      throws DocumentCreationException {
    if (storeResponseSelected()
        && executionEnvironment instanceof ExecutionEnvironment.StoresDocument env) {
      try (var byteArrayInputStream = new ByteArrayInputStream(content)) {
        var document =
            env.documentFactory()
                .create(
                    DocumentCreationRequest.from(byteArrayInputStream)
                        .contentType(getContentType(headers))
                        .build());
        LOGGER.debug("Stored response as document. Document reference: {}", document);
        return document;
      } catch (Exception e) {
        LOGGER.error("Failed to create document: {}", e.getMessage(), e);
        throw new DocumentCreationException("Failed to create document: " + e.getMessage(), e);
      }
    }
    return null;
  }

  private String getContentType(Map<String, Object> headers) {
    return CustomApacheHttpClient.getHeaderIgnoreCase(headers, HttpHeaders.CONTENT_TYPE);
  }

  private boolean storeResponseSelected() {
    return executionEnvironment != null && isStoreResponseSelected;
  }
}
