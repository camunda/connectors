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
/*
package io.camunda.connector.runtime.core.document;

import io.camunda.connector.api.document.DocumentMetadata;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public record DocumentCreationRequest(
    DocumentMetadata metadata,
    InputStream content,
    String documentId,
    String storeId) {

  public static BuilderStepMetadata from(InputStream content) {
    return new BuilderStepMetadata(content);
  }

  public static BuilderStepMetadata from(byte[] content) {
    return new BuilderStepMetadata(new ByteArrayInputStream(content));
  }

  public static class BuilderStepMetadata {
    private final InputStream content;

    public BuilderStepMetadata(InputStream content) {
      this.content = content;
    }

    public BuilderStepDocumentId metadata(DocumentMetadata metadata) {
      return new BuilderStepDocumentId(metadata, content);
    }

    public BuilderStepDocument
  }

  public static class BuilderStepDocumentId {
    private final DocumentMetadata metadata;
    private final InputStream content;

    public BuilderStepDocumentId(DocumentMetadata metadata, InputStream content) {
      this.metadata = metadata;
      this.content = content;
    }

    public BuilderStepStoreId documentId(String documentId) {
      return new BuilderStepStoreId(metadata, content, documentId);
    }
  }
}
*/
