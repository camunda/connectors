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
package io.camunda.connector.document.jackson.deserializer;

import static io.camunda.connector.document.jackson.deserializer.DeserializationUtil.isDocumentReference;
import static io.camunda.connector.document.jackson.deserializer.DeserializationUtil.isOperation;
import static io.camunda.connector.document.jackson.deserializer.DeserializationUtil.requireOperationSuccessOrThrow;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.document.Document;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.operation.IntrinsicOperationExecutor;
import io.camunda.document.operation.IntrinsicOperationResult;
import java.io.IOException;

/**
 * Deserializer for {@link Document} targets. It supports both the case where the source is a
 * document reference and the case where the source is an operation that returns a document.
 */
public class DocumentDeserializer extends AbstractDeserializer<Document> {

  private final IntrinsicOperationResultDeserializer operationDeserializer;
  private final DocumentFactory documentFactory;

  public DocumentDeserializer(
      DocumentFactory documentFactory, IntrinsicOperationExecutor operationExecutor) {
    this.documentFactory = documentFactory;
    this.operationDeserializer = new IntrinsicOperationResultDeserializer(operationExecutor);
  }

  @Override
  protected Document handleJsonNode(JsonNode node, DeserializationContext context)
      throws IOException {
    if (isDocumentReference(node)) {
      final var reference = context.readTreeAsValue(node, DocumentReferenceModel.class);
      return documentFactory.resolve(reference);
    }
    if (isOperation(node)) {
      final IntrinsicOperationResult<?> operation = operationDeserializer.handleJsonNode(node, context);
      return requireOperationSuccessOrThrow(operation, Document.class);
    }
    throw new IllegalArgumentException(
        "Unsupported node format, expected either a document reference or an operation, got: "
            + node);
  }
}
