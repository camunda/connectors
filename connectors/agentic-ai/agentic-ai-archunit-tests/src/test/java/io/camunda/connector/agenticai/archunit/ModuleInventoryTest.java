/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Keeps the {@link AgenticAiModules} registry, which the architecture rules are built from, in step
 * with the Maven modules: every module of the aggregator is registered, and the bundle ships
 * exactly the connectors and their integrations.
 */
class ModuleInventoryTest {

  private static final String GROUP_ID = "io.camunda.connector.agenticai";

  /** The aggregator modules that are not Agentic AI modules with production classes. */
  private static final Set<String> NON_ANALYZED_MODULES =
      Set.of("agentic-ai-bundle", "agentic-ai-archunit-tests");

  @Test
  void everyMavenModuleIsRegisteredAndEveryRegisteredModuleIsAMavenModule() throws Exception {
    final Set<String> mavenModules = new LinkedHashSet<>();
    for (final Element module : children(root(parentPom()), "modules", "module")) {
      mavenModules.add(module.getTextContent().strip());
    }
    mavenModules.removeAll(NON_ANALYZED_MODULES);

    assertThat(mavenModules)
        .as("modules of connectors/agentic-ai/pom.xml, without " + NON_ANALYZED_MODULES)
        .containsExactlyInAnyOrderElementsOf(artifactIds(AgenticAiModules.values()));
  }

  @Test
  void bundleDeclaresExactlyTheConnectorsAndIntegrations() throws Exception {
    final Set<String> bundled = new LinkedHashSet<>();
    for (final Element dependency :
        children(root(module("agentic-ai-bundle")), "dependencies", "dependency")) {
      if (GROUP_ID.equals(text(dependency, "groupId"))) {
        bundled.add(text(dependency, "artifactId"));
      }
    }

    final Set<String> expected =
        artifactIds(
            Arrays.stream(AgenticAiModules.values())
                .filter(module -> module.kind() != AgenticAiModules.Kind.LIBRARY)
                .toArray(AgenticAiModules[]::new));
    assertThat(bundled)
        .as("agentic-ai-bundle dependencies on " + GROUP_ID)
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  private static Set<String> artifactIds(final AgenticAiModules... modules) {
    final Set<String> artifactIds = new LinkedHashSet<>();
    for (final AgenticAiModules module : modules) {
      artifactIds.add(module.artifactId());
    }
    return artifactIds;
  }

  private static Path moduleBaseDir() {
    return Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
  }

  private static Document parentPom() throws IOException, SAXException {
    return parse(moduleBaseDir().resolveSibling("pom.xml"));
  }

  private static Document module(final String directory) throws IOException, SAXException {
    return parse(moduleBaseDir().resolveSibling(directory).resolve("pom.xml"));
  }

  private static Document parse(final Path pom) throws IOException, SAXException {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return factory.newDocumentBuilder().parse(pom.toFile());
    } catch (final ParserConfigurationException e) {
      throw new IllegalStateException("Cannot configure the XML parser", e);
    }
  }

  private static Element root(final Document pom) {
    return pom.getDocumentElement();
  }

  /** The {@code leaf} elements below the direct {@code container} child of the parent element. */
  private static Iterable<Element> children(
      final Element parent, final String container, final String leaf) {
    final Set<Element> elements = new LinkedHashSet<>();
    for (final Element containerElement : childElements(parent, container)) {
      elements.addAll(childElements(containerElement, leaf));
    }
    return elements;
  }

  private static Set<Element> childElements(final Element parent, final String name) {
    final Set<Element> elements = new LinkedHashSet<>();
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof final Element element && name.equals(element.getLocalName())) {
        elements.add(element);
      }
    }
    return elements;
  }

  private static String text(final Element parent, final String name) {
    return childElements(parent, name).stream()
        .findFirst()
        .map(element -> element.getTextContent().strip())
        .orElse("");
  }
}
