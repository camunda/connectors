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
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.operation.IntrinsicOperationExecutor;
import java.io.IOException;

public class StringDeserializer extends AbstractDeserializer<String> {

  private final com.fasterxml.jackson.databind.deser.std.StringDeserializer fallbackDeserializer =
      new com.fasterxml.jackson.databind.deser.std.StringDeserializer();

  private final DocumentDeserializer documentDeserializer;
  private final IntrinsicOperationResultDeserializer operationDeserializer;

  public StringDeserializer(DocumentFactory documentFactory, IntrinsicOperationExecutor operationExecutor) {
    this.documentDeserializer = new DocumentDeserializer(documentFactory, operationExecutor);
    this.operationDeserializer = new IntrinsicOperationResultDeserializer(operationExecutor);
  }

  @Override
  protected String handleJsonNode(JsonNode node, DeserializationContext context)
      throws IOException {
    if (isDocumentReference(node)) {
      final var document = documentDeserializer.handleJsonNode(node, context);
      return document.asBase64();
    }
    if (isOperation(node)) {
      final var operationResult = operationDeserializer.handleJsonNode(node, context);
      return requireOperationSuccessOrThrow(operationResult, String.class);
    }
    // if not document or operation, fallback to default deserialization
    var parser = node.traverse(context.getParser().getCodec());
    parser.nextToken();
    return fallbackDeserializer.deserialize(parser, context);
  }
}
