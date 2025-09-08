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
import static io.camunda.connector.document.jackson.deserializer.DeserializationUtil.isIntrinsicFunction;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.IntrinsicFunctionExecutor;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import java.io.IOException;

public class StringDeserializer extends AbstractDeserializer<String> {

  private final com.fasterxml.jackson.databind.deser.std.StringDeserializer fallbackDeserializer =
      new com.fasterxml.jackson.databind.deser.std.StringDeserializer();

  private final DocumentDeserializer documentDeserializer;
  private final IntrinsicFunctionObjectResultDeserializer intrinsicFunctionDeserializer;

  public StringDeserializer(
      DocumentFactory documentFactory,
      IntrinsicFunctionExecutor intrinsicFunctionExecutor,
      DocumentModuleSettings settings) {
    super(settings);
    this.documentDeserializer =
        new DocumentDeserializer(documentFactory, intrinsicFunctionExecutor, settings);
    this.intrinsicFunctionDeserializer =
        new IntrinsicFunctionObjectResultDeserializer(intrinsicFunctionExecutor, settings);
  }

  @Override
  protected String handleJsonNode(JsonNode node, DeserializationContext context)
      throws IOException {
    if (isDocumentReference(node)) {
      final var document = documentDeserializer.handleJsonNode(node, context);
      return document.asBase64();
    }
    if (isIntrinsicFunction(node)) {
      // counter is decremented in the function deserializer
      final var operationResult = intrinsicFunctionDeserializer.handleJsonNode(node, context);
      if (operationResult instanceof String) {
        return (String) operationResult;
      }
      throw new IllegalArgumentException(
          "Unsupported operation result, expected a string, got: " + operationResult);
    }
    // if not document or operation, fallback to default deserialization
    var parser = node.traverse(context.getParser().getCodec());
    parser.nextToken();
    return fallbackDeserializer.deserialize(parser, context);
  }
}
