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
package io.camunda.connector.document.annotation.jackson.deserializer;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import io.camunda.connector.document.annotation.jackson.DocumentReferenceModel;
import io.camunda.document.factory.DocumentFactory;
import java.io.IOException;

public class StringDocumentDeserializer extends DocumentDeserializerBase<String> {

  private final StringDeserializer fallbackDeserializer = new StringDeserializer();

  public StringDocumentDeserializer(DocumentFactory documentFactory) {
    super(documentFactory);
  }

  @Override
  public String deserializeDocumentReference(
      DocumentReferenceModel reference, DeserializationContext ctx) {

    if (reference.operation().isPresent()) {
      var operationResultSupplier = deserializeOperation(reference, reference.operation().get());
      var result = operationResultSupplier.get();
      if (result instanceof String) {
        return (String) result;
      } else {
        throw new IllegalArgumentException(
            "Unexpected operation result type: " + result.getClass() + ". Expected String");
      }
    }
    // if no operation, return base64 encoded content
    var document = createDocument(reference);
    return document.asBase64();
  }

  @Override
  public String fallback(JsonNode node, DeserializationContext ctx) throws IOException {
    var parser = node.traverse(ctx.getParser().getCodec());
    parser.nextToken();
    return fallbackDeserializer.deserialize(parser, ctx);
  }
}
