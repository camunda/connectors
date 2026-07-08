/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.framework.capabilities.BundledCapabilityMatrixTest.TestObjectMapperConfig;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

/**
 * Verifies that consumer-supplied properties (higher precedence than the bundled YAML) deep-merge
 * into the capability matrix. {@link AgenticAiCapabilitiesConfiguration} loads the bundled YAML
 * itself during {@code setEnvironment(...)}, so importing it plus {@code withPropertyValues(...)}
 * mirrors the layering a library consumer gets when adding entries to their {@code application.yml}
 * — and proves the bundled source is registered at low precedence.
 */
class CapabilityMatrixOverrideTest {

  private static final String PREFIX = "camunda.connector.agenticai.aiagent.framework.capabilities";

  private final ApplicationContextRunner baseRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestObjectMapperConfig.class)
          .withUserConfiguration(AgenticAiCapabilitiesConfiguration.class);

  @Test
  void overrideTunesScalarFieldOnExistingPatternEntry() {
    baseRunner
        .withPropertyValues(
            PREFIX
                + ".anthropic-messages.models.claude-sonnet-4.capabilities.max-output-tokens=999999")
        .run(
            context -> {
              final var caps = resolve(context, "anthropic-messages", "claude-sonnet-4-6");

              assertThat(caps.maxOutputTokens()).isEqualTo(999999);
              // Other bundled fields untouched:
              assertThat(caps.supportsReasoning()).isTrue();
              assertThat(caps.userMessageModalities())
                  .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
            });
  }

  @Test
  void overrideAddsBrandNewModelEntryUnderExistingFamily() {
    baseRunner
        .withPropertyValues(
            PREFIX
                + ".anthropic-messages.models.my-org-tuned-claude.capabilities.max-output-tokens=12345",
            PREFIX
                + ".anthropic-messages.models.my-org-tuned-claude.capabilities.supports-reasoning=true")
        .run(
            context -> {
              final var caps = resolve(context, "anthropic-messages", "my-org-tuned-claude");

              assertThat(caps.maxOutputTokens()).isEqualTo(12345);
              assertThat(caps.supportsReasoning()).isTrue();
              // Inherited from anthropic-messages defaults:
              assertThat(caps.userMessageModalities())
                  .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
              assertThat(caps.toolResultModalities())
                  .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
            });
  }

  @Test
  void overrideReplacesModalityListWholesaleViaSpringBootSemantics() {
    baseRunner
        .withPropertyValues(
            PREFIX
                + ".openai-completions.models.gpt-4o.capabilities.input-modalities.user-message[0]=text")
        .run(
            context -> {
              final var caps = resolve(context, "openai-completions", "gpt-4o-mini");

              assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT);
              // tool_result still inherited from openai-completions defaults:
              assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
            });
  }

  @Test
  void overrideFamilyDefaultScalarDoesNotAffectModelsThatPinItWhileGenericModelsInheritIt() {
    baseRunner
        .withPropertyValues(PREFIX + ".openai-completions.defaults.max-output-tokens=7777")
        .run(
            context -> {
              // gpt-5* pins its own max-output-tokens (sourced from models.dev), so it keeps that
              // value even when the family default changes:
              final var gpt5 = resolve(context, "openai-completions", "gpt-5.5");
              assertThat(gpt5.maxOutputTokens()).isEqualTo(128000);
              assertThat(gpt5.supportsReasoning()).isTrue();

              // A model matching only the gpt-* fallback (no pinned budget) inherits the new
              // default:
              final var generic = resolve(context, "openai-completions", "gpt-3.5-turbo");
              assertThat(generic.maxOutputTokens()).isEqualTo(7777);
              assertThat(generic.supportsReasoning()).isFalse();
            });
  }

  private static ModelCapabilities resolve(
      ApplicationContext context, String apiFamily, String modelId) {
    return context
        .getBean(ModelCapabilitiesResolver.class)
        .resolve(apiFamily, modelId, Optional.empty());
  }
}
