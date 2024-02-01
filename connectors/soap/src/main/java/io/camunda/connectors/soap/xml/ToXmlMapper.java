/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.xml;

import static io.camunda.connectors.soap.xml.TemplateUtil.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.samskivert.mustache.Template;
import io.camunda.connectors.soap.xml.ToXmlMapper.XmlNode.ElementXmlNode;
import io.camunda.connectors.soap.xml.ToXmlMapper.XmlNode.TextXmlNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.w3c.dom.Document;

public class ToXmlMapper {
  public static final Template XML_ELEMENT_TEMPLATE =
      loadTemplate("xml-element-template.mustache", false);
  private final ObjectMapper objectMapper;
  private final String contentName;
  private final String attributePrefix;
  private final boolean prettyPrint;
  private final boolean omitXmlDeclaration;

  public ToXmlMapper(
      ObjectMapper objectMapper,
      String contentName,
      String attributePrefix,
      boolean prettyPrint,
      boolean omitXmlDeclaration) {
    this.objectMapper = objectMapper;
    this.contentName = contentName;
    this.attributePrefix = attributePrefix;
    this.prettyPrint = prettyPrint;
    this.omitXmlDeclaration = omitXmlDeclaration;
  }

  public Document toXml(JsonNode jsonNode) {
    if ((jsonNode instanceof ObjectNode objectNode) && objectNode.size() == 1) {
      String rootName = objectNode.fieldNames().next();
      ElementXmlNode xmlNode = new ElementXmlNode(rootName);
      decorateElement(xmlNode, objectNode.get(rootName));
      return XmlUtilities.xmlStringToDocument(xmlNode.toXmlString());
    } else {
      throw new IllegalStateException(
          "The root of the json document has to an object node with 1 root element");
    }
  }

  public String toXmlPartString(Map<String, Object> jsonNode) {
    return jsonNode.entrySet().stream()
        .map(
            e -> {
              ElementXmlNode node = new ElementXmlNode(e.getKey());
              decorateElement(node, objectMapper.valueToTree(e.getValue()));
              return node.toXmlString();
            })
        .collect(Collectors.joining(""));
  }

  public String toXmlString(Map<String, Object> jsonNode) {
    return XmlUtilities.xmlDocumentToString(
        toXml(objectMapper.valueToTree(jsonNode)), omitXmlDeclaration, prettyPrint);
  }

  private void decorateElement(ElementXmlNode element, JsonNode jsonNode) {
    if (jsonNode instanceof TextNode textNode) {
      element.getValue().add(new TextXmlNode(textNode.textValue()));
    } else if (jsonNode instanceof BooleanNode booleanNode) {
      element.getValue().add(new TextXmlNode(Boolean.toString(booleanNode.booleanValue())));
    } else if (jsonNode instanceof NumericNode numericNode) {
      element.getValue().add(new TextXmlNode(String.valueOf(numericNode.numberValue())));
    } else if (jsonNode instanceof NullNode nullNode) {
      element.getValue().add(new TextXmlNode(nullNode.textValue()));
    } else if (jsonNode instanceof ObjectNode objectNode) {
      objectNode
          .properties()
          .forEach(
              e -> {
                if (e.getKey().equals(contentName)) {
                  if (e.getValue() instanceof TextNode textNode) {
                    element.getValue().add(new TextXmlNode(textNode.textValue()));
                  } else {
                    throw new IllegalStateException("Value has to be text value");
                  }
                } else if (e.getKey().startsWith(attributePrefix)) {
                  if (e.getValue() instanceof TextNode textNode) {
                    element.getAttributes().put(e.getKey().substring(1), textNode.textValue());
                  } else {
                    throw new IllegalStateException("Attributes have to have text values");
                  }
                } else {
                  if (e.getValue() instanceof ArrayNode arrayNode) {
                    arrayNode.forEach(
                        child -> {
                          ElementXmlNode childElement = new ElementXmlNode(e.getKey());
                          decorateElement(childElement, child);
                          element.getValue().add(childElement);
                        });
                  } else if (e.getValue() instanceof ObjectNode
                      || e.getValue() instanceof ValueNode) {
                    ElementXmlNode childElement = new ElementXmlNode(e.getKey());
                    decorateElement(childElement, e.getValue());
                    element.getValue().add(childElement);
                  } else {
                    throw new IllegalStateException(
                        "Unexpected node type " + e.getValue().getClass().getSimpleName());
                  }
                }
              });
    } else {
      throw new IllegalStateException(
          "Unexpected node type " + jsonNode.getClass().getSimpleName());
    }
  }

  abstract static class XmlNode {
    @Override
    public final String toString() {
      return toXmlString();
    }

    protected abstract String toXmlString();

    static class ElementXmlNode extends XmlNode {
      private final Map<String, String> attributes = new HashMap<>();
      private final String elementName;
      private final List<XmlNode> value = new ArrayList<>();

      public ElementXmlNode(String elementName) {
        this.elementName = elementName;
      }

      public String getElementName() {
        return elementName;
      }

      public Map<String, String> getAttributes() {
        return attributes;
      }

      public List<XmlNode> getValue() {
        return value;
      }

      @Override
      protected String toXmlString() {
        Map<String, Object> context = new HashMap<>();
        context.put("elementName", elementName);
        context.put("attributes", attributes);
        context.put("value", String.join("", value.stream().map(XmlNode::toXmlString).toList()));
        return XML_ELEMENT_TEMPLATE.execute(context);
      }
    }

    static class TextXmlNode extends XmlNode {
      private final String value;

      public TextXmlNode(String value) {
        this.value = value;
      }

      public String getValue() {
        return value;
      }

      @Override
      protected String toXmlString() {
        return value;
      }
    }
  }
}
