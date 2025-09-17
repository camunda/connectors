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

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.http.client.document.DocumentCreationException;
import java.io.ByteArrayInputStream;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileResponseHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileResponseHandler.class);
  public static final String CONTENT_TYPE = "Content-Type";
  private final DocumentFactory documentFactory;
  private final boolean isStoreResponseSelected;

  public FileResponseHandler(
      @Nullable DocumentFactory documentFactory, boolean isStoreResponseSelected) {
    this.documentFactory = documentFactory;
    this.isStoreResponseSelected = isStoreResponseSelected;
  }

    public Document handle(Map<String, Object> headers, byte[] content)
      throws DocumentCreationException {
    if (storeResponseSelected()) {
      try (var byteArrayInputStream = new ByteArrayInputStream(content)) {
        var document =
            documentFactory
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
    return headers.entrySet().stream()
        .filter(e -> e.getKey().equalsIgnoreCase(CONTENT_TYPE))
        .map(Map.Entry::getValue)
        .map(Object::toString)
        .findFirst()
        .orElse(null);
  }

  private boolean storeResponseSelected() {
    return documentFactory != null && isStoreResponseSelected;
  }
}
