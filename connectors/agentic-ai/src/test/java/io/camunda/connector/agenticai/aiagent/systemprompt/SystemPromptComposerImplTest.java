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
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;

class SystemPromptComposerImplTest {

  private static final AgentContext CTX = AgentContext.builder().state(AgentState.READY).build();

  private static AgentConfiguration configWithSystemPrompt(SystemPromptConfiguration config) {
    return new AgentConfiguration(null, config, null, null, null, null, null);
  }

  @Test
  void shouldComposeWithBasePromptOnly() {
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of());
    var config = configWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(CTX, config);

    assertThat(result).isEqualTo("Base prompt");
  }

  @Test
  void shouldComposeWithSingleContributor() {
    SystemPromptContributor contributor = (ctx, cfg) -> "Additional instructions";
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of(contributor));
    var config = configWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(CTX, config);

    assertThat(result).isEqualTo("Base prompt\n\nAdditional instructions");
  }

  @Test
  void shouldComposeWithMultipleContributorsInOrder() {
    SystemPromptContributor contributor1 =
        new SystemPromptContributor() {
          @Override
          public String contribute(AgentContext ctx, AgentConfiguration cfg) {
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
          public String contribute(AgentContext ctx, AgentConfiguration cfg) {
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
    var config = configWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(CTX, config);

    assertThat(result).isEqualTo("Base prompt\n\nFirst contribution\n\nSecond contribution");
  }

  @ParameterizedTest
  @NullSource
  @EmptySource
  void shouldSkipNullOrEmptyContributions(String contribution) {
    SystemPromptContributor contributor1 = (ctx, cfg) -> "Valid contribution";
    SystemPromptContributor contributor2 = (ctx, cfg) -> contribution;
    SystemPromptContributor contributor3 = (ctx, cfg) -> "Another valid";

    SystemPromptComposer composer =
        new SystemPromptComposerImpl(List.of(contributor1, contributor2, contributor3));
    var config = configWithSystemPrompt(new SystemPromptConfiguration("Base prompt"));

    String result = composer.compose(CTX, config);

    assertThat(result).isEqualTo("Base prompt\n\nValid contribution\n\nAnother valid");
  }

  @Test
  void shouldHandleEmptyBasePrompt() {
    SystemPromptContributor contributor = (ctx, cfg) -> "Contribution";
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of(contributor));
    var config = configWithSystemPrompt(new SystemPromptConfiguration(""));

    String result = composer.compose(CTX, config);

    assertThat(result).isEqualTo("Contribution");
  }

  @Test
  void shouldHandleNullSystemPromptConfiguration() {
    SystemPromptContributor contributor = (ctx, cfg) -> "Contribution";
    SystemPromptComposer composer = new SystemPromptComposerImpl(List.of(contributor));
    var config = configWithSystemPrompt(null);

    String result = composer.compose(CTX, config);

    assertThat(result).isEqualTo("Contribution");
  }
}
