/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.subprocess.v2;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.StreamingGeminiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Native-Gemini e2e coverage for the extended-thinking configuration surface: proves the element
 * template's {@code enabled}/{@code thinkingBudget}/{@code thinkingLevel} fields reach the wire in
 * the right place and shape, through the real vendor SDK's {@code GenerateContentConfig} builder.
 *
 * <p>{@code thinkingBudget} (Gemini 2.5) and {@code thinkingLevel} (Gemini 3.x) are <b>mutually
 * exclusive</b>: {@code GeminiThinking#isBothThinkingBudgetAndLevelSet()} carries an
 * {@code @AssertFalse} bean-validation constraint, so a single request can never legitimately carry
 * both. Each therefore gets its own test with its own model id — there is no single test that could
 * exercise both, and combining them would only assert a validation failure.
 *
 * <p>Both land under {@code generationConfig.thinkingConfig} (the SDK nests the whole {@code
 * GenerateContentConfig} under {@code generationConfig} for the Developer API, while hoisting
 * {@code tools} and {@code systemInstruction} to the top level).
 */
@SlowTest
class AgentSubProcessGeminiThinkingConfigTests extends BaseGeminiNativeSubProcessTest {

  /** Gemini 2.5 generation: token-budget thinking. */
  private static final String THINKING_BUDGET_MODEL = "gemini-2.5-pro";

  /** Gemini 3.x generation: qualitative thinking level. */
  private static final String THINKING_LEVEL_MODEL = "gemini-3-pro-preview";

  @Test
  void thinkingBudgetAppearsOnTheWireUnderGenerationConfig() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingGeminiChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(THINKING_BUDGET_MODEL)
            .andThen(
                template ->
                    template.property(
                        "provider.googleGemini.model.parameters.thinking.enabled", "=true"))
            .andThen(
                template ->
                    template.property(
                        "provider.googleGemini.model.parameters.thinking.thinkingBudget", "=2048"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var request = soleRecordedRequest();
    assertThat(requestedModel(request))
        .as("model id in the request URL")
        .isEqualTo(THINKING_BUDGET_MODEL);

    final var thinkingConfig = parseBody(request).path("generationConfig").path("thinkingConfig");
    assertThat(thinkingConfig.path("thinkingBudget").asInt())
        .as("generationConfig.thinkingConfig.thinkingBudget")
        .isEqualTo(2048);
    // Thoughts are not returned unless explicitly requested, so the converter always sets this
    // alongside a budget/level - otherwise thinking would be billed but never surfaced.
    assertThat(thinkingConfig.path("includeThoughts").asBoolean())
        .as("generationConfig.thinkingConfig.includeThoughts")
        .isTrue();
    assertThat(thinkingConfig.has("thinkingLevel"))
        .as("thinkingLevel must be absent when a budget is configured")
        .isFalse();
  }

  @Test
  void thinkingLevelAppearsOnTheWireUnderGenerationConfig() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingGeminiChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        model(THINKING_LEVEL_MODEL)
            .andThen(
                template ->
                    template.property(
                        "provider.googleGemini.model.parameters.thinking.enabled", "=true"))
            .andThen(
                template ->
                    template.property(
                        "provider.googleGemini.model.parameters.thinking.thinkingLevel", "high"));

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var request = soleRecordedRequest();
    assertThat(requestedModel(request))
        .as("model id in the request URL")
        .isEqualTo(THINKING_LEVEL_MODEL);

    final var thinkingConfig = parseBody(request).path("generationConfig").path("thinkingConfig");
    assertThat(thinkingConfig.path("thinkingLevel").asText())
        .as("generationConfig.thinkingConfig.thinkingLevel")
        .isEqualTo("HIGH");
    assertThat(thinkingConfig.path("includeThoughts").asBoolean())
        .as("generationConfig.thinkingConfig.includeThoughts")
        .isTrue();
    assertThat(thinkingConfig.has("thinkingBudget"))
        .as("thinkingBudget must be absent when a level is configured")
        .isFalse();
  }

  @Test
  void thinkingConfigCarriesExplicitModelDefaultLevelWhenEnabledWithNeitherFieldSet()
      throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingGeminiChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        template ->
            template.property("provider.googleGemini.model.parameters.thinking.enabled", "=true");

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var thinkingConfig =
        parseBody(soleRecordedRequest()).path("generationConfig").path("thinkingConfig");
    assertThat(thinkingConfig.path("thinkingLevel").asText())
        .as("generationConfig.thinkingConfig.thinkingLevel")
        .isEqualTo("THINKING_LEVEL_UNSPECIFIED");
    assertThat(thinkingConfig.path("includeThoughts").asBoolean())
        .as("generationConfig.thinkingConfig.includeThoughts")
        .isTrue();
    assertThat(thinkingConfig.has("thinkingBudget"))
        .as("thinkingBudget must be absent when neither field is configured")
        .isFalse();
  }

  @Test
  void noThinkingConfigOnTheWireWhenNeitherFieldIsConfigured() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingGeminiChatModelStubs.stubConversation(TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    assertThat(parseBody(soleRecordedRequest()).path("generationConfig").has("thinkingConfig"))
        .as("thinkingConfig must not be sent when thinking is left unconfigured")
        .isFalse();
  }
}
