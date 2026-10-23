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

/**
 * Enforces that the AI Agent connector reaches the chat model providers only through the chat model
 * API: the translation of the request into the provider configurations and the Spring wiring are
 * the only classes that know them. That the providers do not depend on each other, on the agent
 * core or on the connector follows from the module layers in {@link ModuleLayersArchTest}.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class ChatModelProviderAccessArchTest {

  @ArchTest
  static final ArchRule THE_AI_AGENT_DOES_NOT_DEPEND_ON_THE_CHAT_MODEL_PROVIDERS =
      noClasses()
          .that(AgenticAiModules.AI_AGENT.root())
          .and()
          .resideOutsideOfPackages(
              AgenticAiModules.pkg("aiagent.configuration.."),
              AgenticAiModules.pkg("aiagent.translation.."))
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              AgenticAiModules.pkg("aiagent.chatmodel.provider.anthropic.."),
              AgenticAiModules.pkg("aiagent.chatmodel.provider.bedrock.."),
              AgenticAiModules.pkg("aiagent.chatmodel.provider.gemini.."),
              AgenticAiModules.pkg("aiagent.chatmodel.provider.mistral.."),
              AgenticAiModules.pkg("aiagent.chatmodel.provider.openai.."))
          .because("the AI agent reaches the chat model providers only through the chat model API");
}
