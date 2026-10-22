/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

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
    for (final Element module :
        PomFiles.children(PomFiles.root(PomFiles.parentPom()), "modules", "module")) {
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
        PomFiles.children(
            PomFiles.root(PomFiles.modulePom("agentic-ai-bundle")), "dependencies", "dependency")) {
      if (GROUP_ID.equals(PomFiles.text(dependency, "groupId"))) {
        bundled.add(PomFiles.text(dependency, "artifactId"));
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
}
