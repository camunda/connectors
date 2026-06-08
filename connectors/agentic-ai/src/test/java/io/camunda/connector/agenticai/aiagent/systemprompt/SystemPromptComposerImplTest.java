/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.systemprompt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentInvocationInput;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;

class SystemPromptComposerImplTest {

  private static AgentConversation conversationWithSystemPrompt(SystemPromptConfiguration config) {
    var ctx = AgentContext.builder().state(AgentState.READY).build();
    var agentConfig = new AgentConfiguration(null, config, null, null, null, null);
    var input = new AgentInvocationInput(null, List.of());
    return AgentConversation.rehydrate(List.of(), ctx, input, agentConfig);
  }

  @Test
  void shouldComposeWithBasePromptOnly() {
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of());
    var conversation = conversationWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(conversation);

    assertThat(result).isEqualTo("Base prompt");
  }

  @Test
  void shouldComposeWithSingleContributor() {
    SystemPromptContributor contributor = conv -> "Additional instructions";
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of(contributor));
    var conversation = conversationWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(conversation);

    assertThat(result).isEqualTo("Base prompt\n\nAdditional instructions");
  }

  @Test
  void shouldComposeWithMultipleContributorsInOrder() {
    SystemPromptContributor contributor1 =
        new SystemPromptContributor() {
          @Override
          public String contribute(AgentConversation conv) {
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
          public String contribute(AgentConversation conv) {
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
    var conversation = conversationWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(conversation);

    assertThat(result).isEqualTo("Base prompt\n\nFirst contribution\n\nSecond contribution");
  }

  @ParameterizedTest
  @NullSource
  @EmptySource
  void shouldSkipNullOrEmptyContributions(String contribution) {
    SystemPromptContributor contributor1 = conv -> "Valid contribution";
    SystemPromptContributor contributor2 = conv -> contribution;
    SystemPromptContributor contributor3 = conv -> "Another valid";

    SystemPromptComposer composer =
        new SystemPromptComposerImpl(List.of(contributor1, contributor2, contributor3));
    var conversation = conversationWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(conversation);

    assertThat(result).isEqualTo("Base prompt\n\nValid contribution\n\nAnother valid");
  }

  @Test
  void shouldHandleEmptyBasePrompt() {
    SystemPromptContributor contributor = conv -> "Contribution";
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of(contributor));
    var conversation = conversationWithSystemPrompt(new SystemPromptConfiguration(""));

    String result = composer.compose(conversation);

    assertThat(result).isEqualTo("Contribution");
  }

  @Test
  void shouldHandleNullSystemPromptConfiguration() {
    SystemPromptContributor contributor = conv -> "Contribution";
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of(contributor));
    var conversation = conversationWithSystemPrompt(null);

    String result = composer.compose(conversation);

    assertThat(result).isEqualTo("Contribution");
  }
}
