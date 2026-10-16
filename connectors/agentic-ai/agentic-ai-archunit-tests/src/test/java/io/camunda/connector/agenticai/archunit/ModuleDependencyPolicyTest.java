/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Keeps the modules independent of each other and of other connectors, which the package based
 * architecture rules cannot see because they only look at classes below the Agentic AI package
 * root. The policy covers the direct production dependencies in the pom of each module:
 *
 * <ul>
 *   <li>A library depends on libraries only.
 *   <li>A connector depends on libraries only, never on another connector or integration.
 *   <li>An integration depends on libraries and on the one connector it makes usable as a gateway
 *       tool.
 *   <li>No module depends on a connector outside Agentic AI, except for the named exceptions. The
 *       SDK and runtime artifacts of the Camunda connectors are not connectors.
 * </ul>
 */
class ModuleDependencyPolicyTest {

  private static final String AGENTIC_AI_GROUP_ID = "io.camunda.connector.agenticai";
  private static final String CONNECTORS_GROUP_ID = "io.camunda.connector";

  /**
   * The artifacts of the connector SDK and runtime. Every other artifact of the group is a
   * connector.
   */
  private static final Set<String> SDK_AND_RUNTIME_ARTIFACTS =
      Set.of(
          "connector-core",
          "connector-object-mapper",
          "connector-runtime-core",
          "connector-runtime-spring",
          "connector-validation",
          "element-template-generator-annotations",
          "http-client",
          "jackson-datatype-document");

  /**
   * The connector the integration modules make usable as a gateway tool, which they may depend on.
   */
  private static final Map<AgenticAiModules, AgenticAiModules> INTEGRATED_CONNECTORS =
      Map.of(
          AgenticAiModules.MCP_INTEGRATION, AgenticAiModules.MCP_CLIENT,
          AgenticAiModules.A2A_INTEGRATION, AgenticAiModules.A2A_CLIENT);

  /**
   * Connectors outside Agentic AI that a module depends on, because they are maintained by another
   * team. The A2A client uses the webhook authorization and HMAC verification of the webhook
   * connector (ADR 015, follow-up).
   */
  private static final Map<AgenticAiModules, Set<String>> ACCEPTED_CONNECTOR_DEPENDENCIES =
      Map.of(AgenticAiModules.A2A_CLIENT, Set.of("connector-webhook"));

  @ParameterizedTest
  @EnumSource(AgenticAiModules.class)
  void moduleDependsOnlyOnWhatThePolicyAllows(final AgenticAiModules module)
      throws IOException, SAXException {
    final Document pom = PomFiles.modulePom(module.artifactId());

    assertThat(violations(module, pom)).as("dependencies of " + module.artifactId()).isEmpty();
  }

  @Test
  void connectorDependingOnAnotherConnectorIsRejected() throws Exception {
    final Document pom =
        pom(
            dependency(AGENTIC_AI_GROUP_ID, "connector-mcp", "compile"),
            dependency(AGENTIC_AI_GROUP_ID, "agentic-ai-common", "compile"));

    assertThat(violations(AgenticAiModules.AI_AGENT, pom))
        .containsExactly(
            "connector-aiagent must not depend on connector-mcp, a connector or integration of"
                + " Agentic AI");
  }

  @Test
  void integrationDependingOnTheClientOfAnotherIntegrationIsRejected() throws Exception {
    final Document pom =
        pom(
            dependency(AGENTIC_AI_GROUP_ID, "connector-mcp", "compile"),
            dependency(AGENTIC_AI_GROUP_ID, "connector-a2a", "compile"),
            dependency(AGENTIC_AI_GROUP_ID, "aiagent-api", "compile"));

    assertThat(violations(AgenticAiModules.MCP_INTEGRATION, pom))
        .containsExactly(
            "aiagent-mcp-integration must not depend on connector-a2a, a connector or integration"
                + " of Agentic AI");
  }

  @Test
  void libraryDependingOnAConnectorIsRejected() throws Exception {
    final Document pom = pom(dependency(AGENTIC_AI_GROUP_ID, "aiagent-mcp-integration", "compile"));

    assertThat(violations(AgenticAiModules.API, pom))
        .containsExactly(
            "aiagent-api must not depend on aiagent-mcp-integration, a connector or integration of"
                + " Agentic AI");
  }

  @Test
  void dependingOnAnotherConnectorOfTheCamundaConnectorsIsRejected() throws Exception {
    final Document pom = pom(dependency(CONNECTORS_GROUP_ID, "connector-webhook", "compile"));

    assertThat(violations(AgenticAiModules.MCP_CLIENT, pom))
        .containsExactly(
            "connector-mcp must not depend on io.camunda.connector:connector-webhook, a connector"
                + " that is not part of Agentic AI");
  }

  @Test
  void namedExceptionIsAccepted() throws Exception {
    final Document pom = pom(dependency(CONNECTORS_GROUP_ID, "connector-webhook", "compile"));

    assertThat(violations(AgenticAiModules.A2A_CLIENT, pom)).isEmpty();
  }

  @Test
  void sdkAndRuntimeArtifactsAndTestDependenciesAreIgnored() throws Exception {
    final Document pom =
        pom(
            dependency(CONNECTORS_GROUP_ID, "connector-runtime-spring", "compile"),
            dependency(CONNECTORS_GROUP_ID, "connector-core", "provided"),
            dependency(CONNECTORS_GROUP_ID, "connector-webhook", "test"),
            dependency(AGENTIC_AI_GROUP_ID, "connector-mcp", "test"));

    assertThat(violations(AgenticAiModules.AI_AGENT, pom)).isEmpty();
  }

  private static List<String> violations(final AgenticAiModules module, final Document pom) {
    final List<String> violations = new ArrayList<>();
    for (final Element dependency :
        PomFiles.children(PomFiles.root(pom), "dependencies", "dependency")) {
      final String scope = PomFiles.text(dependency, "scope");
      if (scope.equals("test")) {
        continue;
      }
      final String groupId = PomFiles.text(dependency, "groupId");
      final String artifactId = PomFiles.text(dependency, "artifactId");
      if (groupId.equals(AGENTIC_AI_GROUP_ID)) {
        violations.addAll(agenticAiDependencyViolations(module, artifactId));
      } else if (groupId.equals(CONNECTORS_GROUP_ID)
          && !SDK_AND_RUNTIME_ARTIFACTS.contains(artifactId)
          && !ACCEPTED_CONNECTOR_DEPENDENCIES.getOrDefault(module, Set.of()).contains(artifactId)) {
        violations.add(
            "%s must not depend on %s:%s, a connector that is not part of Agentic AI"
                .formatted(module.artifactId(), groupId, artifactId));
      }
    }
    return violations;
  }

  private static List<String> agenticAiDependencyViolations(
      final AgenticAiModules module, final String artifactId) {
    final Optional<AgenticAiModules> dependency =
        Arrays.stream(AgenticAiModules.values())
            .filter(candidate -> candidate.artifactId().equals(artifactId))
            .findFirst();
    final boolean allowed =
        dependency
            .map(
                target ->
                    target.kind() == AgenticAiModules.Kind.LIBRARY
                        || target == INTEGRATED_CONNECTORS.get(module))
            .orElse(false);
    return allowed
        ? List.of()
        : List.of(
            "%s must not depend on %s, a connector or integration of Agentic AI"
                .formatted(module.artifactId(), artifactId));
  }

  private static Document pom(final String... dependencies) throws IOException, SAXException {
    return PomFiles.parse(
        "<project><dependencies>" + String.join("", dependencies) + "</dependencies></project>");
  }

  private static String dependency(
      final String groupId, final String artifactId, final String scope) {
    return "<dependency><groupId>%s</groupId><artifactId>%s</artifactId><scope>%s</scope></dependency>"
        .formatted(groupId, artifactId, scope);
  }
}
