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

import static io.camunda.connector.document.jackson.deserializer.DeserializationUtil.isDocumentModeWrapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.document.Document;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Replaces the default {@code List<Document>} deserializer to additionally accept the
 * {@code @TemplateDocumentProperty} wrapper shape with a {@code documentMode} discriminator.
 *
 * <p>For {@code documentMode = "single"}, returns a single-element list built from the embedded
 * single-document wrapper. For {@code documentMode = "multiple"}, returns the array bound to {@code
 * multiple.expression}. For plain JSON arrays (or single objects), iterates and resolves each
 * element via the registered {@code Document} deserializer.
 */
public class DocumentListDeserializer extends JsonDeserializer<List<Document>> {

  public DocumentListDeserializer(JsonDeserializer<?> defaultDeserializer) {
    // The default collection deserializer is intentionally unused. Jackson can't supply a
    // fully-resolved instance to a SimpleModule deserializer, so we resolve elements ourselves via
    // ctxt.readTreeAsValue(..., Document.class) which always goes through our DocumentDeserializer.
  }

  @Override
  public List<Document> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = ctxt.readTree(p);

    if (node == null || node.isNull()) {
      return null;
    }

    if (isDocumentModeWrapper(node)) {
      return unwrapModeWrapper(node, ctxt);
    }

    if (node.isArray()) {
      List<Document> out = new ArrayList<>(node.size());
      Iterator<JsonNode> it = node.elements();
      while (it.hasNext()) {
        JsonNode element = it.next();
        if (element == null || element.isNull()) {
          continue;
        }
        Document doc = ctxt.readTreeAsValue(element, Document.class);
        if (doc != null) {
          out.add(doc);
        }
      }
      return out;
    }

    // A single object that's a document reference (or wrapper) — auto-wrap in a 1-element list,
    // matching Jackson's default ACCEPT_SINGLE_VALUE_AS_ARRAY behavior.
    Document doc = ctxt.readTreeAsValue(node, Document.class);
    return doc == null ? Collections.emptyList() : List.of(doc);
  }

  private List<Document> unwrapModeWrapper(JsonNode wrapper, DeserializationContext ctxt)
      throws IOException {
    JsonNode modeNode = wrapper.get("documentMode");
    if (modeNode == null || modeNode.isNull()) {
      return Collections.emptyList();
    }
    String mode = modeNode.asText();
    return switch (mode) {
      case "single" -> singleAsList(wrapper.get("single"), ctxt);
      case "multiple" -> multipleAsList(wrapper.get("multiple"), ctxt);
      default ->
          throw new IllegalArgumentException(
              "Unknown documentMode '" + mode + "'. Expected single or multiple.");
    };
  }

  private List<Document> singleAsList(JsonNode singleWrapper, DeserializationContext ctxt)
      throws IOException {
    if (singleWrapper == null || singleWrapper.isNull()) {
      return Collections.emptyList();
    }
    Document doc = ctxt.readTreeAsValue(singleWrapper, Document.class);
    if (doc == null) {
      return Collections.emptyList();
    }
    List<Document> out = new ArrayList<>(1);
    out.add(doc);
    return out;
  }

  private List<Document> multipleAsList(JsonNode multipleWrapper, DeserializationContext ctxt)
      throws IOException {
    if (multipleWrapper == null || multipleWrapper.isNull()) {
      return Collections.emptyList();
    }
    JsonNode expression = multipleWrapper.get("expression");
    if (expression == null || expression.isNull()) {
      return Collections.emptyList();
    }
    if (!expression.isArray()) {
      Document single = ctxt.readTreeAsValue(expression, Document.class);
      return single == null ? Collections.emptyList() : List.of(single);
    }
    List<Document> out = new ArrayList<>(expression.size());
    Iterator<JsonNode> it = expression.elements();
    while (it.hasNext()) {
      Document doc = ctxt.readTreeAsValue(it.next(), Document.class);
      if (doc != null) {
        out.add(doc);
      }
    }
    return out;
  }
}
