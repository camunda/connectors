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
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentSource.ByteArrayDocumentSource;
import io.camunda.connector.api.document.DocumentSource.ReferenceDocumentSource;
import io.camunda.connector.runtime.core.document.DocumentFactory;
import java.io.IOException;
import java.util.Base64;

/** A Jackson deserializer to handle {@link Document} objects. */
public class SimpleDocumentDeserializer extends JsonDeserializer<Document> {

  public SimpleDocumentDeserializer(DocumentFactory documentFactory) {
    this.documentFactory = documentFactory;
  }

  private final DocumentFactory documentFactory;

  @Override
  public Document deserialize(JsonParser jsonParser, DeserializationContext ctx)
      throws IOException {

    JsonNode node = jsonParser.readValueAsTree();
    if (node == null || node.isNull()) {
      return null;
    }

    if (node.has(DocumentReference.DISCRIMINATOR_KEY)) {
      return deserializeReference(node, ctx);
    } else if (node.isTextual()) {
      return deserializeBase64(node);
    } else {
      throw new IllegalArgumentException(
          "Unsupported document format. Expected a document reference or a base64 encoded document, got: "
              + node);
    }
  }

  private Document deserializeReference(JsonNode node, DeserializationContext ctx)
      throws IOException {
    var reference = ctx.readTreeAsValue(node, DocumentReference.class);
    var source = new ReferenceDocumentSource(reference);
    return documentFactory.from(source).build();
  }

  private Document deserializeBase64(JsonNode node) {
    var content = node.textValue();
    try {
      var bytes = Base64.getDecoder().decode(content);
      var source = new ByteArrayDocumentSource(bytes);
      return documentFactory.from(source).build();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid base64 encoded document: " + content, e);
    }
  }
}
