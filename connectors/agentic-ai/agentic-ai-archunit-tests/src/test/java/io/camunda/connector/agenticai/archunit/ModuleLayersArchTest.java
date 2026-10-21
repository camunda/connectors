/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;
import java.util.Arrays;

/**
 * Enforces the dependency direction between the package roots of the Agentic AI modules. The layers
 * are the package roots; a package root is a layer of its own, so the rules hold whether the
 * modules are built together or separately.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class ModuleLayersArchTest {

  @ArchTest static final ArchRule MODULES_DEPEND_ONLY_ON_THE_MODULES_BELOW_THEM = layers();

  private static ArchRule layers() {
    LayeredArchitecture architecture =
        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .ensureAllClassesAreContainedInArchitecture();
    for (final var module : AgenticAiModules.values()) {
      architecture = architecture.layer(module.layerName()).definedBy(module.root());
    }

    // Common is accessible from every module.
    return architecture
        .whereLayer(AgenticAiModules.MODEL.layerName())
        .mayOnlyBeAccessedByLayers(
            names(
                AgenticAiModules.API,
                AgenticAiModules.PROVIDER_SUPPORT,
                AgenticAiModules.TOOLS_SCHEMA,
                AgenticAiModules.AD_HOC_CONNECTOR,
                AgenticAiModules.MCP_INTEGRATION,
                AgenticAiModules.A2A_INTEGRATION,
                AgenticAiModules.AI_AGENT))
        .whereLayer(AgenticAiModules.API.layerName())
        .mayOnlyBeAccessedByLayers(
            names(
                AgenticAiModules.PROVIDER_SUPPORT,
                AgenticAiModules.TOOLS_SCHEMA,
                AgenticAiModules.AD_HOC_CONNECTOR,
                AgenticAiModules.MCP_INTEGRATION,
                AgenticAiModules.A2A_INTEGRATION,
                AgenticAiModules.AI_AGENT))
        .whereLayer(AgenticAiModules.PROVIDER_SUPPORT.layerName())
        .mayOnlyBeAccessedByLayers(names(AgenticAiModules.AI_AGENT))
        .whereLayer(AgenticAiModules.TOOLS_SCHEMA.layerName())
        .mayOnlyBeAccessedByLayers(
            names(AgenticAiModules.AD_HOC_CONNECTOR, AgenticAiModules.AI_AGENT))
        .whereLayer(AgenticAiModules.MCP_CLIENT.layerName())
        .mayOnlyBeAccessedByLayers(names(AgenticAiModules.MCP_INTEGRATION))
        .whereLayer(AgenticAiModules.A2A_CLIENT.layerName())
        .mayOnlyBeAccessedByLayers(names(AgenticAiModules.A2A_INTEGRATION))
        .whereLayer(AgenticAiModules.AD_HOC_CONNECTOR.layerName())
        .mayNotBeAccessedByAnyLayer()
        .whereLayer(AgenticAiModules.MCP_INTEGRATION.layerName())
        .mayNotBeAccessedByAnyLayer()
        .whereLayer(AgenticAiModules.A2A_INTEGRATION.layerName())
        .mayNotBeAccessedByAnyLayer()
        .whereLayer(AgenticAiModules.AI_AGENT.layerName())
        .mayNotBeAccessedByAnyLayer()
        .because("each module depends only on the modules below it");
  }

  private static String[] names(final AgenticAiModules... modules) {
    return Arrays.stream(modules).map(AgenticAiModules::layerName).toArray(String[]::new);
  }
}
