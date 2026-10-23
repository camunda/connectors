/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Keeps the built-in runtime tools of the AI Agent connector on the public extension point: a
 * built-in tool is a {@code RuntimeTool} like any other and uses what a tool of a third party can
 * use, the API, the model, the common library, the connector SDK, Jackson, jspecify and the JDK. It
 * does not reach into the AI Agent connector.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class BuiltInRuntimeToolsArchTest {

  @ArchTest
  static final ArchRule BUILT_IN_RUNTIME_TOOLS_DEPEND_ONLY_ON_THE_PUBLIC_EXTENSION_API =
      classes()
          .that()
          .resideInAPackage(AgenticAiModules.pkg("aiagent.tool.runtime.builtin.."))
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              AgenticAiModules.pkg("aiagent.tool.runtime.builtin.."),
              AgenticAiModules.pkg("aiagent.api.."),
              AgenticAiModules.pkg("aiagent.model.."),
              AgenticAiModules.pkg("common.."),
              "io.camunda.connector.api..",
              "com.fasterxml.jackson..",
              "org.jspecify..",
              "java..")
          .because(
              "a built-in runtime tool proves that the runtime tool API is sufficient: it uses no"
                  + " code of the AI Agent connector besides its own package");
}
