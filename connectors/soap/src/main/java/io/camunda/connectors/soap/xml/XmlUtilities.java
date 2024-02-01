/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * {@code XmlUtilities} provides basic XML parsing utility functions to print and parse XML {@code
 * Document} or string.
 */
public final class XmlUtilities {

  private XmlUtilities() {
    throw new AssertionError();
  }

  public static Document xmlStringToDocument(String xmlString) {
    return xmlStringToDocument(xmlString, true);
  }

  private static Document xmlStringToDocument(String xmlString, boolean namespaceAware) {
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(namespaceAware);
    try {
      return documentBuilderFactory
          .newDocumentBuilder()
          .parse(new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8)));
    } catch (SAXException | IOException | ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
  }

  public static void appendXmlStringToNode(
      String xmlString, Map<String, String> namespaces, Node node) {
    Document document;
    Node clone;
    if (namespaces == null || namespaces.isEmpty()) {
      document = xmlStringToDocument(xmlString);
      clone = document.getDocumentElement().cloneNode(true);
    } else {
      document = xmlStringToDocument(buildWrapper(xmlString, namespaces));
      clone =
          getChildNodes(document.getDocumentElement()).stream()
              .filter(n -> Element.class.isAssignableFrom(n.getClass()))
              .findFirst()
              .get()
              .cloneNode(true);
    }
    node.getOwnerDocument().adoptNode(clone);
    node.appendChild(clone);
  }

  private static String buildWrapper(String xmlString, Map<String, String> namespaces) {
    return "<wrapper "
        + namespaces.entrySet().stream()
            .map(e -> String.format("xmlns:%s=\"%s\"", e.getKey(), e.getValue()))
            .collect(Collectors.joining(" "))
        + ">"
        + xmlString
        + "</wrapper>";
  }

  private static List<Node> getChildNodes(Node node) {
    List<Node> result = new ArrayList<>();
    for (int i = 0; i < node.getChildNodes().getLength(); i++) {
      result.add(node.getChildNodes().item(i));
    }
    return result;
  }

  public static String xmlDocumentToString(
      Document doc, boolean omitXmlDeclaration, boolean prettyPrint) {
    StringWriter sw = new StringWriter();
    StreamResult result = new StreamResult(sw);
    TransformerFactory transformerFactory = TransformerFactory.newInstance();

    try (InputStream in =
        XmlUtilities.class.getClassLoader().getResourceAsStream("prettyprint.xsl")) {
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "no");
      if (prettyPrint) {
        transformer =
            transformerFactory.newTransformer(new StreamSource(new InputStreamReader(in)));
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      }
      transformer.setOutputProperty(
          OutputKeys.OMIT_XML_DECLARATION, omitXmlDeclaration ? "yes" : "no");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
      synchronized (doc) {
        transformer.transform(new DOMSource(doc), result);
      }
    } catch (IOException | TransformerException e) {
      throw new RuntimeException(e);
    }
    return sw.toString();
  }
}
