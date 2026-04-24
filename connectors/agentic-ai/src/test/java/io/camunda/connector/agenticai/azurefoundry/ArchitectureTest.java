/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "io.camunda.connector.agenticai.azurefoundry")
class ArchitectureTest {

  @ArchTest
  static final ArchRule sdk_layer_must_not_depend_on_langchain4j =
      noClasses()
          .that()
          .resideInAPackage("..azurefoundry..")
          .and()
          .resideOutsideOfPackage("..azurefoundry.langchain4j..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.langchain4j..")
          .because(
              "Only the adapter subpackage may depend on langchain4j; the rest must "
                  + "survive a future langchain4j replacement without modification.");

  @ArchTest
  static final ArchRule azurefoundry_must_not_depend_on_agent_framework_internals =
      noClasses()
          .that()
          .resideInAPackage("..azurefoundry..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..aiagent.agent..", "..aiagent.memory..", "..adhoctoolsschema..")
          .because(
              "The Foundry packages must stay decoupled from agent framework internals; "
                  + "the only integration point is ChatModel.");
}
