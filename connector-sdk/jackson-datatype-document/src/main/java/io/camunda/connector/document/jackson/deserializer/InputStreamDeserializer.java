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

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.document.Document;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.intrinsic.IntrinsicFunctionExecutor;
import java.io.IOException;
import java.io.InputStream;

public class InputStreamDeserializer extends AbstractDeserializer<InputStream> {

  private final DocumentFactory documentFactory;
  private final IntrinsicFunctionExecutor operationExecutor;

  public InputStreamDeserializer(
      DocumentFactory documentFactory, IntrinsicFunctionExecutor intrinsicFunctionExecutor) {
    this.documentFactory = documentFactory;
    this.operationExecutor = intrinsicFunctionExecutor;
  }

  @Override
  protected InputStream handleJsonNode(JsonNode node, DeserializationContext context)
      throws IOException {
    if (isDocumentReference(node)) {
      final var document =
          new DocumentDeserializer(documentFactory, operationExecutor)
              .handleJsonNode(node, context);
      return document.asInputStream();
    }
    if (DeserializationUtil.isOperation(node)) {
      final var operationResult =
          new IntrinsicFunctionObjectResultDeserializer(operationExecutor)
              .handleJsonNode(node, context);
      if (operationResult instanceof Document document) {
        return document.asInputStream();
      }
      throw new IllegalArgumentException(
          "Unsupported operation result, expected a document, got: " + operationResult);
    }
    throw new IllegalArgumentException(
        "Node cannot be deserialized as InputStream, expected either a document reference or an operation, got: "
            + node);
  }
}
