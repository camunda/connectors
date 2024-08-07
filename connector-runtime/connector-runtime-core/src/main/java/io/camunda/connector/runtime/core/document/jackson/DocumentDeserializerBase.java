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
package io.camunda.connector.runtime.core.document.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentOperation;
import io.camunda.connector.api.document.DocumentOperationResult;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentSource.ReferenceDocumentSource;
import io.camunda.connector.runtime.core.document.DocumentFactory;
import io.camunda.connector.runtime.core.document.DocumentOperationExecutor;
import java.io.IOException;

public abstract class DocumentDeserializerBase<T> extends JsonDeserializer<T> {

  protected final DocumentOperationExecutor operationExecutor;
  protected final DocumentFactory documentFactory;

  public DocumentDeserializerBase(
      DocumentOperationExecutor operationExecutor, DocumentFactory documentFactory) {
    this.operationExecutor = operationExecutor;
    this.documentFactory = documentFactory;
  }

  @Override
  public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

    JsonNode node = p.readValueAsTree();
    if (node == null || node.isNull()) {
      return null;
    }
    if (isDocumentReference(node)) {
      var reference = toReference(node, ctxt);
      return deserializeDocumentReference(reference, ctxt);
    }
    return fallback(node, ctxt);
  }

  /** Will be invoked when the deserializable data is a document reference. */
  public abstract T deserializeDocumentReference(
      DocumentReference reference, DeserializationContext ctx) throws IOException;

  /**
   * Will be invoked when the deserializable data is not a document reference. Deserializers should
   * implement this method to provide a fallback behavior.
   */
  public abstract T fallback(JsonNode node, DeserializationContext ctx) throws IOException;

  protected boolean isDocumentReference(JsonNode node) {
    return node.has(DocumentReference.DISCRIMINATOR_KEY);
  }

  protected DocumentReference toReference(JsonNode node, DeserializationContext ctx)
      throws IOException {

    if (!isDocumentReference(node)) {
      throw new IllegalArgumentException(
          "Unsupported document format. Expected a document reference, got: " + node);
    }
    return ctx.readTreeAsValue(node, DocumentReference.class);
  }

  protected void ensureNoOperation(DocumentReference reference) {
    if (reference.operation().isPresent()) {
      throw new IllegalArgumentException(
          "Unsupported document format. Expected a document reference without operation, got: "
              + reference);
    }
  }

  protected Document createDocument(DocumentReference reference) {
    return documentFactory.from(new ReferenceDocumentSource(reference)).build();
  }

  protected DocumentOperationResult<?> deserializeOperation(
      DocumentReference reference, DocumentOperation operation) {
    return () -> operationExecutor.execute(operation, createDocument(reference));
  }
}
