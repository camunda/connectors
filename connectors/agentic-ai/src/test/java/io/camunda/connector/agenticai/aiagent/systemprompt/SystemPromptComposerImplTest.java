/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.systemprompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;

class SystemPromptComposerImplTest {

  @Test
  void shouldComposeWithBasePromptOnly() {
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of());
    AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
    AgentContext agentContext = mock(AgentContext.class);
    SystemPromptConfiguration config = new SystemPromptConfiguration("Base prompt");

    String result = composer.composeSystemPrompt(executionContext, agentContext, config);

    assertThat(result).isEqualTo("Base prompt");
  }

  @Test
  void shouldComposeWithSingleContributor() {
    SystemPromptContributor contributor = (ctx, agentCtx) -> "Additional instructions";
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of(contributor));

    AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
    AgentContext agentContext = mock(AgentContext.class);
    SystemPromptConfiguration config = new SystemPromptConfiguration("Base prompt");

    String result = composer.composeSystemPrompt(executionContext, agentContext, config);

    assertThat(result).isEqualTo("Base prompt\n\nAdditional instructions");
  }

  @Test
  void shouldComposeWithMultipleContributorsInOrder() {
    SystemPromptContributor contributor1 =
        new SystemPromptContributor() {
          @Override
          public String contributeSystemPrompt(AgentExecutionContext ctx, AgentContext agentCtx) {
            return "First contribution";
          }

          @Override
          public int getOrder() {
            return 10;
          }
        };

    SystemPromptContributor contributor2 =
        new SystemPromptContributor() {
          @Override
          public String contributeSystemPrompt(AgentExecutionContext ctx, AgentContext agentCtx) {
            return "Second contribution";
          }

          @Override
          public int getOrder() {
            return 20;
          }
        };

    // Add in reverse order to test sorting
    SystemPromptComposer composer =
        new SystemPromptComposerImpl(List.of(contributor2, contributor1));

    AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
    AgentContext agentContext = mock(AgentContext.class);
    SystemPromptConfiguration config = new SystemPromptConfiguration("Base prompt");

    String result = composer.composeSystemPrompt(executionContext, agentContext, config);

    assertThat(result).isEqualTo("Base prompt\n\nFirst contribution\n\nSecond contribution");
  }

  @ParameterizedTest
  @NullSource
  @EmptySource
  void shouldSkipNullOrEmptyContributions(String contribution) {
    SystemPromptContributor contributor1 = (ctx, agentCtx) -> "Valid contribution";
    SystemPromptContributor contributor2 = (ctx, agentCtx) -> contribution;
    SystemPromptContributor contributor3 = (ctx, agentCtx) -> "Another valid";

    SystemPromptComposer composer =
        new SystemPromptComposerImpl(List.of(contributor1, contributor2, contributor3));

    AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
    AgentContext agentContext = mock(AgentContext.class);
    SystemPromptConfiguration config = new SystemPromptConfiguration("Base prompt");

    String result = composer.composeSystemPrompt(executionContext, agentContext, config);

    assertThat(result).isEqualTo("Base prompt\n\nValid contribution\n\nAnother valid");
  }

  @Test
  void shouldHandleEmptyBasePrompt() {
    SystemPromptContributor contributor = (ctx, agentCtx) -> "Contribution";
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of(contributor));

    AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
    AgentContext agentContext = mock(AgentContext.class);
    SystemPromptConfiguration config = new SystemPromptConfiguration("");

    String result = composer.composeSystemPrompt(executionContext, agentContext, config);

    assertThat(result).isEqualTo("Contribution");
  }
}
