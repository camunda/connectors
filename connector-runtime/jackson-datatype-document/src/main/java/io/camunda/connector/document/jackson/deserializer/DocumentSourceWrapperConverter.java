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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.document.jackson.DocumentReferenceModel;

/**
 * Converts the {@code @TemplateDocumentProperty} wrapper shape (with a {@code documentSource}
 * discriminator and per-source sub-fields) into the canonical {@link DocumentReferenceModel} shape
 * keyed by {@link DocumentReferenceModel#DISCRIMINATOR_KEY}, so that the existing sealed-type
 * deserializer can handle it.
 *
 * <p>Camunda source unwraps to whatever the user bound to {@code camundaReference} (typically an
 * existing Camunda document reference). Inline / external sources rebuild the canonical model from
 * the user's per-source sub-fields.
 */
public final class DocumentSourceWrapperConverter {

  private DocumentSourceWrapperConverter() {}

  /**
   * Returns the canonical document-reference JSON for the given wrapper node, or {@code null} when
   * the wrapper resolves to a missing document (e.g. camunda source with no reference selected).
   */
  public static JsonNode toDocumentReferenceNode(JsonNode wrapper) {
    JsonNode sourceNode = wrapper.get("documentSource");
    if (sourceNode == null || sourceNode.isNull()) {
      return null;
    }
    String source = sourceNode.asText();
    return switch (source) {
      case "camunda" -> unwrapCamunda(wrapper);
      case "inline" -> buildInline(wrapper);
      case "external" -> buildExternal(wrapper);
      default ->
          throw new IllegalArgumentException(
              "Unknown documentSource '" + source + "'. Expected camunda, inline, or external.");
    };
  }

  private static JsonNode unwrapCamunda(JsonNode wrapper) {
    JsonNode ref = wrapper.get("camundaReference");
    if (ref == null || ref.isNull()) {
      return null;
    }
    return ref;
  }

  private static JsonNode buildInline(JsonNode wrapper) {
    JsonNode inline = wrapper.get("inline");
    if (inline == null || inline.isNull()) {
      return null;
    }
    ObjectNode out = JsonNodeFactory.instance.objectNode();
    out.put(DocumentReferenceModel.DISCRIMINATOR_KEY, "inline");
    JsonNode content = inline.get("content");
    if (content != null) {
      out.set("content", content);
    }
    JsonNode fileName = inline.get("fileName");
    if (fileName != null) {
      out.set("name", fileName);
    }
    JsonNode contentType = inline.get("contentType");
    if (contentType != null) {
      out.set("contentType", contentType);
    }
    return out;
  }

  private static JsonNode buildExternal(JsonNode wrapper) {
    JsonNode external = wrapper.get("external");
    if (external == null || external.isNull()) {
      return null;
    }
    ObjectNode out = JsonNodeFactory.instance.objectNode();
    out.put(DocumentReferenceModel.DISCRIMINATOR_KEY, "external");
    JsonNode url = external.get("url");
    if (url != null) {
      out.set("url", url);
    }
    JsonNode fileName = external.get("fileName");
    if (fileName != null) {
      out.set("name", fileName);
    }
    return out;
  }
}
