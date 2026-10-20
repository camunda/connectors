/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Reads the Maven poms of the Agentic AI modules with the JDK XML parser. */
final class PomFiles {

  private PomFiles() {}

  /** The aggregator pom of the Agentic AI modules. */
  static Document parentPom() throws IOException, SAXException {
    return parse(moduleBaseDir().resolveSibling("pom.xml"));
  }

  /** The pom of the module in the given directory next to the architecture tests. */
  static Document modulePom(final String directory) throws IOException, SAXException {
    return parse(moduleBaseDir().resolveSibling(directory).resolve("pom.xml"));
  }

  static Element root(final Document pom) {
    return pom.getDocumentElement();
  }

  /** The {@code leaf} elements below the direct {@code container} child of the parent element. */
  static List<Element> children(final Element parent, final String container, final String leaf) {
    final List<Element> elements = new ArrayList<>();
    for (final Element containerElement : childElements(parent, container)) {
      elements.addAll(childElements(containerElement, leaf));
    }
    return elements;
  }

  static List<Element> childElements(final Element parent, final String name) {
    final List<Element> elements = new ArrayList<>();
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof final Element element && name.equals(element.getLocalName())) {
        elements.add(element);
      }
    }
    return elements;
  }

  /** The stripped text of the first direct child with the given name, or an empty string. */
  static String text(final Element parent, final String name) {
    return childElements(parent, name).stream()
        .findFirst()
        .map(element -> element.getTextContent().strip())
        .orElse("");
  }

  private static Path moduleBaseDir() {
    return Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
  }

  private static Document parse(final Path pom) throws IOException, SAXException {
    try (final InputStream in = Files.newInputStream(pom)) {
      return parse(in);
    }
  }

  private static Document parse(final InputStream pom) throws IOException, SAXException {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return factory.newDocumentBuilder().parse(pom);
    } catch (final ParserConfigurationException e) {
      throw new IllegalStateException("Cannot configure the XML parser", e);
    }
  }
}
