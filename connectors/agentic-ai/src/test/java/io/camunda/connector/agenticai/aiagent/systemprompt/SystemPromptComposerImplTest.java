/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.systemprompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;

class SystemPromptComposerImplTest {

  private static final AgentContext CTX = AgentContext.builder().state(AgentState.READY).build();

  private static AgentExecutionContext executionContextWithSystemPrompt(
      SystemPromptConfiguration systemPrompt) {
    var configuration =
        new AgentConfiguration(null, systemPrompt, null, null, null, null, null, null);
    var executionContext = mock(AgentExecutionContext.class);
    when(executionContext.configuration()).thenReturn(configuration);
    return executionContext;
  }

  @Test
  void shouldComposeWithBasePromptOnly() {
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of());
    var executionContext =
        executionContextWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(executionContext, CTX);

    assertThat(result).isEqualTo("Base prompt");
  }

  @Test
  void shouldComposeWithSingleContributor() {
    SystemPromptContributor contributor = (execCtx, agentCtx) -> "Additional instructions";
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of(contributor));
    var executionContext =
        executionContextWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(executionContext, CTX);

    assertThat(result).isEqualTo("Base prompt\n\nAdditional instructions");
  }

  @Test
  void shouldComposeWithMultipleContributorsInOrder() {
    SystemPromptContributor contributor1 =
        new SystemPromptContributor() {
          @Override
          public String contribute(AgentExecutionContext executionContext, AgentContext ctx) {
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
          public String contribute(AgentExecutionContext executionContext, AgentContext ctx) {
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
    var executionContext =
        executionContextWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(executionContext, CTX);

    assertThat(result).isEqualTo("Base prompt\n\nFirst contribution\n\nSecond contribution");
  }

  @ParameterizedTest
  @NullSource
  @EmptySource
  void shouldSkipNullOrEmptyContributions(String contribution) {
    SystemPromptContributor contributor1 = (execCtx, agentCtx) -> "Valid contribution";
    SystemPromptContributor contributor2 = (execCtx, agentCtx) -> contribution;
    SystemPromptContributor contributor3 = (execCtx, agentCtx) -> "Another valid";

    SystemPromptComposer composer =
        new SystemPromptComposerImpl(List.of(contributor1, contributor2, contributor3));
    var executionContext =
        executionContextWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(executionContext, CTX);

    assertThat(result).isEqualTo("Base prompt\n\nValid contribution\n\nAnother valid");
  }

  @Test
  void shouldHandleEmptyBasePrompt() {
    SystemPromptContributor contributor = (execCtx, agentCtx) -> "Contribution";
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of(contributor));
    var executionContext = executionContextWithSystemPrompt(new SystemPromptConfiguration(""));

    String result = composer.compose(executionContext, CTX);

    assertThat(result).isEqualTo("Contribution");
  }

  @Test
  void shouldHandleBlankBasePrompt() {
    SystemPromptContributor contributor = (execCtx, agentCtx) -> "Contribution";
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of(contributor));
    var executionContext = executionContextWithSystemPrompt(new SystemPromptConfiguration(null));

    String result = composer.compose(executionContext, CTX);

    assertThat(result).isEqualTo("Contribution");
  }
}
