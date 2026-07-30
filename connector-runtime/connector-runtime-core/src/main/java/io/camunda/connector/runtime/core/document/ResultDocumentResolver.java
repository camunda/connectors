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
package io.camunda.connector.runtime.core.document;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.feel.FeelConnectorFunctionProvider;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves {@code createDocument(...)} sentinel markers produced by {@code
 * io.camunda.connector.feel.function.CreateDocumentFunction} into real {@link Document} references.
 * Walks the full JSON tree returned by evaluating a FEEL result/error expression, since the
 * sentinel can appear anywhere in that tree, not only at the root.
 */
public class ResultDocumentResolver {

  private final DocumentFactory documentFactory;

  public ResultDocumentResolver(DocumentFactory documentFactory) {
    this.documentFactory = documentFactory;
  }

  public Object resolve(JsonNode node) {
    if (node.isObject()) {
      if (isCreateDocumentSentinel(node)) {
        return createDocument(node.get("value"));
      }
      Map<String, Object> result = new LinkedHashMap<>();
      node.fields()
          .forEachRemaining(entry -> result.put(entry.getKey(), resolve(entry.getValue())));
      return result;
    }
    if (node.isArray()) {
      List<Object> result = new ArrayList<>();
      node.forEach(element -> result.add(resolve(element)));
      return result;
    }
    return scalarValue(node);
  }

  private boolean isCreateDocumentSentinel(JsonNode node) {
    JsonNode discriminator = node.get(FeelConnectorFunctionProvider.RESULT_FUNCTION_TYPE_PROPERTY);
    return discriminator != null
        && discriminator.isTextual()
        && FeelConnectorFunctionProvider.CREATE_DOCUMENT_TYPE_VALUE.equals(
            discriminator.textValue());
  }

  private Document createDocument(JsonNode value) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      throw new ConnectorInputException(
          "createDocument() was called without a value to convert into a document");
    }
    String content;
    String name = null;
    String contentType = null;
    if (value.isTextual()) {
      content = value.textValue();
    } else if (value.isObject()) {
      content = firstNonBlankText(value, "content", "data");
      name = firstNonBlankText(value, "name", "fileName");
      contentType = firstNonBlankText(value, "contentType");
    } else {
      throw new ConnectorInputException(
          "createDocument() expects a string or an object argument, got: " + value.getNodeType());
    }
    if (content == null) {
      throw new ConnectorInputException(
          "createDocument() requires a 'content' or 'data' field containing a base64-encoded"
              + " string");
    }
    byte[] decoded;
    try {
      // MIME decoder tolerates whitespace/line-wraps (76-char MIME chunks), which is common in
      // base64 returned by third-party APIs, while still rejecting genuinely invalid input.
      decoded = Base64.getMimeDecoder().decode(content);
    } catch (IllegalArgumentException e) {
      throw new ConnectorInputException(
          "createDocument() 'content'/'data' is not valid base64: " + e.getMessage());
    }
    String fileName = name != null ? name : UUID.randomUUID().toString();
    String resolvedContentType = MimeTypeResolver.resolveContentType(contentType, fileName);
    return documentFactory.create(
        DocumentCreationRequest.from(decoded)
            .contentType(resolvedContentType)
            .fileName(fileName)
            .build());
  }

  private String firstNonBlankText(JsonNode object, String... keys) {
    for (String key : keys) {
      JsonNode fieldValue = object.get(key);
      if (fieldValue != null && fieldValue.isTextual() && !fieldValue.textValue().isBlank()) {
        return fieldValue.textValue();
      }
    }
    return null;
  }

  private Object scalarValue(JsonNode node) {
    if (node.isTextual()) return node.textValue();
    if (node.isBoolean()) return node.booleanValue();
    if (node.isNull() || node.isMissingNode()) return null;
    // Use numberValue() rather than longValue()/doubleValue(): those silently truncate/wrap
    // (e.g. a 30-digit integer wraps to an incorrect Long), whereas numberValue() returns the
    // precise Number subtype (BigInteger, BigDecimal, Long, Integer, Double, ...) Jackson parsed.
    if (node.isNumber()) return node.numberValue();
    return node.asText();
  }
}
