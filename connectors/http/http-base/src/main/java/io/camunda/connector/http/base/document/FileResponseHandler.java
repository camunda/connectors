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
package io.camunda.connector.http.base.document;

import io.camunda.connector.http.base.ExecutionEnvironment;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.document.Document;
import io.camunda.document.store.DocumentCreationRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileResponseHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileResponseHandler.class);
  public static final String CONTENT_TYPE = "Content-Type";
  private final ExecutionEnvironment executionEnvironment;
  private final boolean isStoreResponseSelected;

  public FileResponseHandler(
      @Nullable ExecutionEnvironment executionEnvironment, boolean isStoreResponseSelected) {
    this.executionEnvironment = executionEnvironment;
    this.isStoreResponseSelected = isStoreResponseSelected;
  }

  public Document handleCloudFunctionResult(HttpCommonResult result) {
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

  public Document handle(Map<String, Object> headers, byte[] content) {
    if (storeResponseSelected()
        && executionEnvironment instanceof ExecutionEnvironment.StoresDocument env) {
      try (var byteArrayInputStream = new ByteArrayInputStream(content)) {
          return env.documentFactory()
                  .create(
                          DocumentCreationRequest.from(byteArrayInputStream)
                                  .contentType(getContentType(headers))
                                  .build());
      } catch (Exception e) {
        LOGGER.error("Failed to create document: {}", e.getMessage(), e);
        throw new DocumentCreationException("Failed to create document: " + e.getMessage(), e);
      }
    }
    return null;
  }

  private String getContentType(Map<String, Object> headers) {
    return headers.entrySet().stream()
        .filter(e -> e.getKey().equalsIgnoreCase(CONTENT_TYPE))
        .map(Map.Entry::getValue)
        .map(Object::toString)
        .findFirst()
        .orElse(null);
  }

  private boolean storeResponseSelected() {
    return executionEnvironment != null && isStoreResponseSelected;
  }
}
