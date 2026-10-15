/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The Agentic AI Maven modules, the package roots they own, and the means to tell which module a
 * class was loaded from.
 */
enum AgenticAiModules {
  COMMON("agentic-ai-common", "Common", under("common")),
  MODEL("aiagent-model", "Model", under("aiagent.model")),
  API("aiagent-api", "Api", under("aiagent.api")),
  TOOLS_SCHEMA("aiagent-tools-schema", "ToolsSchema", under("tools.schema")),
  AD_HOC_CONNECTOR("connector-ad-hoc-tools-schema", "AdHocConnector", under("adhoctoolsschema")),
  MCP_CLIENT("connector-mcp", "McpClient", under("mcp.client")),
  MCP_INTEGRATION("aiagent-mcp-integration", "McpIntegration", under("mcp.integration")),
  A2A_CLIENT("connector-a2a", "A2aClient", under("a2a.client")),
  A2A_INTEGRATION("aiagent-a2a-integration", "A2aIntegration", under("a2a.integration")),
  AI_AGENT(
      "connector-aiagent",
      "AiAgent",
      under("aiagent").and(resideOutsideOfPackages(pkg("aiagent.model.."), pkg("aiagent.api.."))));

  static final String ROOT = "io.camunda.connector.agenticai";

  private static final String ARCHIVE_SEPARATOR = "!/";
  private static final Pattern VERSION_AND_EXTENSION =
      Pattern.compile("-\\d+(\\.\\d+)*(-SNAPSHOT)?\\.jar");

  private final String artifactId;
  private final String layerName;
  private final DescribedPredicate<JavaClass> root;

  AgenticAiModules(
      final String artifactId, final String layerName, final DescribedPredicate<JavaClass> root) {
    this.artifactId = artifactId;
    this.layerName = layerName;
    this.root = root;
  }

  String artifactId() {
    return artifactId;
  }

  String layerName() {
    return layerName;
  }

  /** Matches the classes in the package root this module owns. */
  DescribedPredicate<JavaClass> root() {
    return root.as("reside in the package root of module " + artifactId);
  }

  /** Matches the classes loaded from this module's build output (directory or jar). */
  DescribedPredicate<JavaClass> loadedFromModule() {
    return DescribedPredicate.describe(
        "are loaded from module " + artifactId,
        javaClass -> moduleOf(javaClass).filter(this::equals).isPresent());
  }

  /**
   * Resolves the module whose build output contains the class: either the reactor output directory
   * {@code <artifactId>/target/classes} or a jar named {@code <artifactId>-<version>.jar}.
   */
  static Optional<AgenticAiModules> moduleOf(final JavaClass javaClass) {
    return javaClass
        .getSource()
        .map(source -> source.getUri().toString())
        .flatMap(
            location ->
                Arrays.stream(values()).filter(module -> module.isLocatedIn(location)).findFirst());
  }

  private boolean isLocatedIn(final String location) {
    if (location.contains("/" + artifactId + "/target/classes/")) {
      return true;
    }
    final int archiveEnd = location.indexOf(ARCHIVE_SEPARATOR);
    final String archive = archiveEnd >= 0 ? location.substring(0, archiveEnd) : location;
    final String fileName = archive.substring(archive.lastIndexOf('/') + 1);
    return fileName.startsWith(artifactId)
        && VERSION_AND_EXTENSION.matcher(fileName.substring(artifactId.length())).matches();
  }

  static String pkg(final String relativePackage) {
    return ROOT + "." + relativePackage;
  }

  private static DescribedPredicate<JavaClass> under(final String relativePackage) {
    return resideInAPackage(pkg(relativePackage + ".."));
  }
}
