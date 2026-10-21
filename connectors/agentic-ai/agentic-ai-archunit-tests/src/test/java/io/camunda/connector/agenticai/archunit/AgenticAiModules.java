/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;

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
  COMMON("agentic-ai-common", "Common", Kind.LIBRARY, under("common")),
  MODEL("aiagent-model", "Model", Kind.LIBRARY, under("aiagent.model")),
  API("aiagent-api", "Api", Kind.LIBRARY, under("aiagent.api")),
  PROVIDER_SUPPORT(
      "aiagent-provider-support",
      "ProviderSupport",
      Kind.LIBRARY,
      under("aiagent.chatmodel.connection")
          .or(under("aiagent.chatmodel.envelope"))
          .or(under("aiagent.chatmodel.wire"))),
  AZURE_SUPPORT(
      "aiagent-azure-support",
      "AzureSupport",
      Kind.LIBRARY,
      under("aiagent.chatmodel.provider.azure").or(under("aiagent.proxy.azure"))),
  AWS_SUPPORT(
      "aiagent-aws-support",
      "AwsSupport",
      Kind.LIBRARY,
      under("aiagent.chatmodel.provider.aws").or(under("aiagent.proxy.aws"))),
  PROVIDER_OPENAI(
      "aiagent-provider-openai",
      "ProviderOpenAi",
      Kind.LIBRARY,
      under("aiagent.chatmodel.provider.openai")),
  PROVIDER_MISTRAL(
      "aiagent-provider-mistral",
      "ProviderMistral",
      Kind.LIBRARY,
      under("aiagent.chatmodel.provider.mistral")),
  PROVIDER_ANTHROPIC(
      "aiagent-provider-anthropic",
      "ProviderAnthropic",
      Kind.LIBRARY,
      under("aiagent.chatmodel.provider.anthropic")),
  PROVIDER_GEMINI(
      "aiagent-provider-gemini",
      "ProviderGemini",
      Kind.LIBRARY,
      under("aiagent.chatmodel.provider.gemini")),
  PROVIDER_BEDROCK(
      "aiagent-provider-bedrock",
      "ProviderBedrock",
      Kind.LIBRARY,
      under("aiagent.chatmodel.provider.bedrock")),
  PROVIDER_LANGCHAIN4J(
      "aiagent-provider-langchain4j",
      "ProviderLangChain4J",
      Kind.LIBRARY,
      under("aiagent.chatmodel.provider.langchain4j")),
  MEMORY_AGENTCORE(
      "aiagent-memory-agentcore",
      "MemoryAgentCore",
      Kind.LIBRARY,
      under("aiagent.memory.conversation.awsagentcore")),
  TEST_SUPPORT("aiagent-test-support", "TestSupport", Kind.LIBRARY, under("aiagent.testsupport")),
  TOOLS_SCHEMA("aiagent-tools-schema", "ToolsSchema", Kind.LIBRARY, under("tools.schema")),
  AD_HOC_CONNECTOR(
      "connector-ad-hoc-tools-schema", "AdHocConnector", Kind.CONNECTOR, under("adhoctoolsschema")),
  MCP_CLIENT("connector-mcp", "McpClient", Kind.CONNECTOR, under("mcp.client")),
  MCP_INTEGRATION(
      "aiagent-mcp-integration", "McpIntegration", Kind.INTEGRATION, under("mcp.integration")),
  A2A_CLIENT("connector-a2a", "A2aClient", Kind.CONNECTOR, under("a2a.client")),
  A2A_INTEGRATION(
      "aiagent-a2a-integration", "A2aIntegration", Kind.INTEGRATION, under("a2a.integration")),
  AI_AGENT(
      "connector-aiagent",
      "AiAgent",
      Kind.CONNECTOR,
      DescribedPredicate.describe(
          "reside in the AI Agent package root, outside the roots of the other modules",
          javaClass ->
              under("aiagent").test(javaClass)
                  && Arrays.stream(values())
                      .filter(module -> !module.artifactId.equals("connector-aiagent"))
                      .noneMatch(module -> module.root.test(javaClass))));

  /** What a module is: shared code, a connector of its own, or the glue between two connectors. */
  enum Kind {
    /** Shared code without a connector of its own. */
    LIBRARY,
    /** A connector, shipped in the agentic AI bundle. */
    CONNECTOR,
    /** The glue that lets the AI Agent connector use another connector as a gateway tool. */
    INTEGRATION
  }

  static final String ROOT = "io.camunda.connector.agenticai";

  private static final String ARCHIVE_SEPARATOR = "!/";
  private static final Pattern VERSION_AND_EXTENSION =
      Pattern.compile("-\\d+(\\.\\d+)*(-SNAPSHOT)?\\.jar");

  private final String artifactId;
  private final String layerName;
  private final Kind kind;
  private final DescribedPredicate<JavaClass> root;

  AgenticAiModules(
      final String artifactId,
      final String layerName,
      final Kind kind,
      final DescribedPredicate<JavaClass> root) {
    this.artifactId = artifactId;
    this.layerName = layerName;
    this.kind = kind;
    this.root = root;
  }

  String artifactId() {
    return artifactId;
  }

  String layerName() {
    return layerName;
  }

  Kind kind() {
    return kind;
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
