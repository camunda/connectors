/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces that conversation stores and chat model factories are only reached through their
 * registries: the concrete implementations are only used by their own packages and the wiring.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class RegistryEncapsulationArchTest {

  private static final String CONVERSATION_STORE =
      AgenticAiModules.pkg("aiagent.api.conversation.ConversationStore");
  private static final String CHAT_MODEL_FACTORY =
      AgenticAiModules.pkg("aiagent.api.chatmodel.ChatModelFactory");

  @ArchTest
  static final ArchRule CONVERSATION_STORES_ARE_ONLY_USED_BY_THEIR_PACKAGE_AND_THE_WIRING =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              AgenticAiModules.pkg("aiagent.memory.conversation.."),
              AgenticAiModules.pkg("aiagent.configuration.."))
          .should()
          .dependOnClassesThat(implementationsOf(CONVERSATION_STORE))
          .because("conversation stores are resolved through the ConversationStoreRegistry");

  @ArchTest
  static final ArchRule CHAT_MODEL_FACTORIES_ARE_ONLY_USED_BY_THEIR_PACKAGES_AND_THE_WIRING =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              AgenticAiModules.pkg("aiagent.chatmodel.."),
              AgenticAiModules.pkg("aiagent.configuration.."))
          .should()
          .dependOnClassesThat(implementationsOf(CHAT_MODEL_FACTORY))
          .because("chat model factories are resolved through the ChatModelRegistry");

  private static DescribedPredicate<JavaClass> implementationsOf(final String type) {
    return assignableTo(type).and(DescribedPredicate.not(INTERFACES)).as("implement " + type);
  }
}
