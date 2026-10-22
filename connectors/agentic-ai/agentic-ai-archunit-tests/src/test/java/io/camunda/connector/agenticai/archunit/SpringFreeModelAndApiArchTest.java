/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Keeps the model and the API of the AI Agent connector free of the Spring framework. */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class SpringFreeModelAndApiArchTest {

  @ArchTest
  static final ArchRule MODEL_AND_API_DO_NOT_DEPEND_ON_SPRING =
      noClasses()
          .that()
          .resideInAnyPackage(
              AgenticAiModules.pkg("aiagent.model.."), AgenticAiModules.pkg("aiagent.api.."))
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..")
          .because("the model and API modules can be used without the Spring framework");
}
