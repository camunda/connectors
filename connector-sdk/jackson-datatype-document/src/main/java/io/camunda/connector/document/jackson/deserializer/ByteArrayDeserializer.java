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
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.PrimitiveArrayDeserializers;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.IntrinsicFunctionExecutor;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import java.io.IOException;

public class ByteArrayDeserializer extends AbstractDeserializer<byte[]> {

  private final JsonDeserializer<?> fallbackDeserializer =
      PrimitiveArrayDeserializers.forType(byte.class);

  private final DocumentDeserializer documentDeserializer;
  private final IntrinsicFunctionObjectResultDeserializer intrinsicFunctionDeserializer;

  public ByteArrayDeserializer(
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
  protected byte[] handleJsonNode(JsonNode node, DeserializationContext context)
      throws IOException {

    if (isDocumentReference(node)) {
      final var document = documentDeserializer.handleJsonNode(node, context);
      return document.asByteArray();
    }
    if (DeserializationUtil.isIntrinsicFunction(node)) {
      // counter is decremented in the function deserializer
      final var functionResult = intrinsicFunctionDeserializer.handleJsonNode(node, context);
      if (functionResult instanceof Document document) {
        return document.asByteArray();
      }
      throw new IllegalArgumentException(
          "Unsupported operation result, expected a document, got: " + functionResult);
    }

    // if not document or operation, fallback to default deserialization
    var parser = node.traverse(context.getParser().getCodec());
    parser.nextToken();
    return (byte[]) fallbackDeserializer.deserialize(parser, context);
  }
}
