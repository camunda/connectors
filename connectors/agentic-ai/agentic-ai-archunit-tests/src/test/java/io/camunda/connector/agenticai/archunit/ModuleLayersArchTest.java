/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.A2A_CLIENT;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.A2A_INTEGRATION;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.AD_HOC_CONNECTOR;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.AI_AGENT;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.API;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.AWS_SUPPORT;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.AZURE_SUPPORT;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.COMMON;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.MCP_CLIENT;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.MCP_INTEGRATION;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.MODEL;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.PROVIDER_ANTHROPIC;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.PROVIDER_BEDROCK;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.PROVIDER_GEMINI;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.PROVIDER_MISTRAL;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.PROVIDER_OPENAI;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.PROVIDER_SUPPORT;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.TEST_SUPPORT;
import static io.camunda.connector.agenticai.archunit.AgenticAiModules.TOOLS_SCHEMA;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the dependency direction between the package roots of the Agentic AI modules. The layers
 * are the package roots; a package root is a layer of its own, so the rules hold whether the
 * modules are built together or separately. Every module may use the common library; the other
 * dependencies are listed in {@link #DEPENDS_ON}, and the rule derives who may access a module from
 * them.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class ModuleLayersArchTest {

  /** The modules each module may depend on, besides the common library. */
  private static final Map<AgenticAiModules, Set<AgenticAiModules>> DEPENDS_ON =
      new EnumMap<>(AgenticAiModules.class);

  static {
    dependsOn(API, MODEL);
    dependsOn(PROVIDER_SUPPORT, API, MODEL);
    dependsOn(AZURE_SUPPORT);
    dependsOn(AWS_SUPPORT);
    dependsOn(PROVIDER_OPENAI, PROVIDER_SUPPORT, AZURE_SUPPORT, API, MODEL);
    dependsOn(PROVIDER_MISTRAL, PROVIDER_SUPPORT, PROVIDER_OPENAI, API, MODEL);
    dependsOn(PROVIDER_ANTHROPIC, PROVIDER_SUPPORT, AZURE_SUPPORT, AWS_SUPPORT, API, MODEL);
    dependsOn(PROVIDER_GEMINI, PROVIDER_SUPPORT, API, MODEL);
    dependsOn(PROVIDER_BEDROCK, PROVIDER_SUPPORT, AWS_SUPPORT, API, MODEL);
    dependsOn(TEST_SUPPORT, PROVIDER_SUPPORT, API, MODEL);
    dependsOn(TOOLS_SCHEMA, API, MODEL);
    dependsOn(AD_HOC_CONNECTOR, TOOLS_SCHEMA, API, MODEL);
    dependsOn(MCP_CLIENT);
    dependsOn(MCP_INTEGRATION, MCP_CLIENT, API, MODEL);
    dependsOn(A2A_CLIENT);
    dependsOn(A2A_INTEGRATION, A2A_CLIENT, API, MODEL);
    dependsOn(
        AI_AGENT,
        PROVIDER_SUPPORT,
        AZURE_SUPPORT,
        AWS_SUPPORT,
        PROVIDER_OPENAI,
        PROVIDER_MISTRAL,
        PROVIDER_ANTHROPIC,
        PROVIDER_GEMINI,
        PROVIDER_BEDROCK,
        TOOLS_SCHEMA,
        API,
        MODEL);
  }

  @ArchTest static final ArchRule MODULES_DEPEND_ONLY_ON_THE_MODULES_BELOW_THEM = layers();

  private static void dependsOn(
      final AgenticAiModules module, final AgenticAiModules... dependencies) {
    DEPENDS_ON.put(module, Set.of(dependencies));
  }

  private static ArchRule layers() {
    LayeredArchitecture architecture =
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .ensureAllClassesAreContainedInArchitecture();
    for (final var module : AgenticAiModules.values()) {
      architecture = architecture.layer(module.layerName()).definedBy(module.root());
    }

    // Common is accessible from every module.
    for (final var module : AgenticAiModules.values()) {
      if (module == COMMON) {
        continue;
      }
      final var dependents =
          DEPENDS_ON.entrySet().stream()
              .filter(entry -> entry.getValue().contains(module))
              .map(Map.Entry::getKey)
              .toArray(AgenticAiModules[]::new);
      architecture =
          dependents.length == 0
              ? architecture.whereLayer(module.layerName()).mayNotBeAccessedByAnyLayer()
              : architecture
                  .whereLayer(module.layerName())
                  .mayOnlyBeAccessedByLayers(names(dependents));
    }
    return architecture.because("each module depends only on the modules below it");
  }

  private static String[] names(final AgenticAiModules... modules) {
    return Arrays.stream(modules).map(AgenticAiModules::layerName).toArray(String[]::new);
  }
}
