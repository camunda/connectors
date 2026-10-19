/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces the dependencies of the HTTP proxy support of the AI Agent connector. */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class ProxySupportArchTest {

  private static final String PROXY = AgenticAiModules.pkg("aiagent.proxy..");
  private static final String COMMON = AgenticAiModules.pkg("common..");

  @ArchTest
  static final ArchRule PROXY_SUPPORT_DEPENDS_ONLY_ON_COMMON =
      classes()
          .that()
          .resideInAPackage(PROXY)
          .should()
          .onlyDependOnClassesThat(
              resideOutsideOfPackage(AgenticAiModules.ROOT + "..")
                  .or(resideInAnyPackage(PROXY, COMMON)))
          .because(
              "the HTTP proxy support is shared by the chat model providers and conversation stores"
                  + " and moves along with them when they are extracted into their own modules");
}
