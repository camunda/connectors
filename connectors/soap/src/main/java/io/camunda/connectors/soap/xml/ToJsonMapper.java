/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.xml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class ToJsonMapper {
  private final ObjectMapper objectMapper;
  private final boolean preserveNamespaces;
  private final AttributeMode attributeMode;
  private final String contentName;
  private final String attributePrefix;

  public ToJsonMapper(
      ObjectMapper objectMapper,
      boolean preserveNamespaces,
      AttributeMode attributeMode,
      String contentName,
      String attributePrefix) {
    this.objectMapper = objectMapper;
    this.preserveNamespaces = preserveNamespaces;
    this.attributeMode = attributeMode;
    this.contentName = contentName;
    this.attributePrefix = attributePrefix;
  }

  public Map<String, Object> toJson(String xmlString) {
    try {
      return objectMapper.readValue(
          toJson(XmlUtilities.xmlStringToDocument(xmlString)).toString(), new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error while mapping json", e);
    }
  }

  public JsonNode toJson(Document document) {
    assert document != null;
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.set(
        preserveNamespaces
            ? document.getDocumentElement().getTagName()
            : document.getDocumentElement().getLocalName(),
        toJson(document.getDocumentElement()));
    return node;
  }

  private JsonNode toJson(Node element) {
    if (element instanceof Text text) {
      return JsonNodeFactory.instance.textNode(text.getWholeText());
    }
    if (isTextContainer(element)) {
      return JsonNodeFactory.instance.textNode(((Text) element.getFirstChild()).getWholeText());
    }
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    // handle child nodes
    for (int i = 0; i < element.getChildNodes().getLength(); i++) {
      Node childNode = element.getChildNodes().item(i);
      if (childNode instanceof Element childElement) {
        String name = preserveNamespaces ? childElement.getTagName() : childElement.getLocalName();
        if (node.has(name)) {
          JsonNode existingNode = node.get(name);
          if (existingNode instanceof ArrayNode array) {
            array.add(toJson(childElement));
          } else {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            array.add(existingNode);
            array.add(toJson(childElement));
            node.set(name, array);
          }
        } else {
          node.set(name, toJson(childElement));
        }
      } else if (childNode instanceof Text childText) {
        if (!childText.getWholeText().trim().isBlank()) {
          node.put(contentName, childText.getWholeText());
        }
      }
    }
    // handle attributes
    if (!AttributeMode.omit.equals(attributeMode)) {
      for (int i = 0; i < element.getAttributes().getLength(); i++) {
        Attr attribute = (Attr) element.getAttributes().item(i);
        if (preserveNamespaces || !attribute.getName().startsWith("xmlns")) {
          String fieldName = preserveNamespaces ? attribute.getName() : attribute.getLocalName();
          if (AttributeMode.prefix.equals(attributeMode)) {
            fieldName = attributePrefix + fieldName;
          }
          node.put(fieldName, attribute.getValue());
        }
      }
    }
    return node;
  }

  private boolean isTextContainer(Node element) {
    if (element.hasAttributes()) {
      return false;
    }
    if (element.getChildNodes().getLength() == 0) {
      return false;
    }
    for (int i = 0; i < element.getChildNodes().getLength(); i++) {
      Node childNode = element.getChildNodes().item(i);
      if (!(childNode instanceof Text)) {
        return false;
      }
    }
    return true;
  }

  public enum AttributeMode {
    omit,
    noPrefix,
    prefix
  }
}
