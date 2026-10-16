/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces that sibling packages (the chat model providers, the conversation stores and the
 * sub-packages of the A2A client and the tools schema) do not depend on each other cyclically.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class PackageCyclesArchTest {

  @ArchTest
  static final ArchRule CHAT_MODEL_PROVIDERS_ARE_FREE_OF_CYCLES =
      freeOfCycles("aiagent.chatmodel.provider.(*)..");

  @ArchTest
  static final ArchRule CONVERSATION_STORES_ARE_FREE_OF_CYCLES =
      freeOfCycles("aiagent.memory.conversation.(*)..");

  @ArchTest
  static final ArchRule A2A_CLIENT_PACKAGES_ARE_FREE_OF_CYCLES = freeOfCycles("a2a.client.(*)..");

  @ArchTest
  static final ArchRule TOOLS_SCHEMA_PACKAGES_ARE_FREE_OF_CYCLES =
      freeOfCycles("tools.schema.(*)..");

  private static ArchRule freeOfCycles(final String relativeSlicePattern) {
    return slices()
        .matching(AgenticAiModules.pkg(relativeSlicePattern))
        .should()
        .beFreeOfCycles()
        .because("cyclic package dependencies prevent extracting or changing a package on its own");
  }
}
