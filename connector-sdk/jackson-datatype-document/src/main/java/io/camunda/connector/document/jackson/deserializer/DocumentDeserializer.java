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

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.document.Document;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.intrinsic.IntrinsicFunctionExecutor;
import java.io.IOException;
import java.util.List;

/**
 * Deserializer for {@link Document} targets. It supports both the case where the source is a
 * document reference and the case where the source is an operation that returns a document.
 */
public class DocumentDeserializer extends AbstractDeserializer<Document> {

  private final IntrinsicFunctionObjectResultDeserializer operationDeserializer;
  private final DocumentFactory documentFactory;

  public DocumentDeserializer(
      DocumentFactory documentFactory, IntrinsicFunctionExecutor intrinsicFunctionExecutor) {
    this.documentFactory = documentFactory;
    this.operationDeserializer =
        new IntrinsicFunctionObjectResultDeserializer(intrinsicFunctionExecutor);
  }

  @Override
  protected Document handleJsonNode(JsonNode node, DeserializationContext context)
      throws IOException {
    if (isDocumentReference(node)) {
      final var reference = context.readTreeAsValue(node, DocumentReferenceModel.class);
      return documentFactory.resolve(reference);
    }
    if (node.isArray()) {
      List<JsonNode> elements = Lists.newArrayList(node.elements());
      if (elements.size() == 1 && isDocumentReference(elements.get(0))) {
        final var reference =
            context.readTreeAsValue(elements.get(0), DocumentReferenceModel.class);
        return documentFactory.resolve(reference);
      } else {
        throw new IllegalArgumentException(
            "Cant bind a multi element document array to a single document.");
      }
    }
    if (isOperation(node)) {
      final Object operationResult = operationDeserializer.handleJsonNode(node, context);
      if (operationResult instanceof Document) {
        return (Document) operationResult;
      }
      throw new IllegalArgumentException(
          "Unsupported operation result, expected a document, got: " + operationResult);
    }
    throw new IllegalArgumentException(
        "Unsupported node format, expected either a document reference or an operation, got: "
            + node);
  }
}
