/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel.AnthropicThinking;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicModel.ThinkingDisplay;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AnthropicReasoningValidatorTest {

  private static final String MODEL_ID = "claude-sonnet-4-6";

  private static AnthropicModelParameters params(
      @Nullable AnthropicThinking thinking,
      @Nullable AnthropicEffort effort,
      @Nullable String customEffort) {
    return new AnthropicModelParameters(null, null, null, null, thinking, effort, customEffort);
  }

  private static AnthropicReasoningCapabilities reasoning(
      List<ThinkingMode> thinkingModes, List<AnthropicEffort> effortLevels) {
    return new AnthropicReasoningCapabilities(thinkingModes, effortLevels);
  }

  private static void validate(
      @Nullable AnthropicModelParameters params,
      @Nullable AnthropicReasoningCapabilities reasoning,
      boolean modelMatched) {
    AnthropicReasoningValidator.validate(params, reasoning, modelMatched, MODEL_ID);
  }

  // --- Rule 1: thinking or effort set but the (matched) model declares no reasoning ----------

  @Test
  void failsWhenThinkingSetAndMatchedModelDeclaresNoReasoning() {
    final var params = params(new AnthropicThinking(ThinkingMode.ENABLED, 2048, null), null, null);

    assertThatThrownBy(() -> validate(params, null, true))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
  }

  @Test
  void failsWhenEffortSetAndMatchedModelDeclaresNoReasoning() {
    final var params = params(null, AnthropicEffort.HIGH, null);

    assertThatThrownBy(() -> validate(params, null, true))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
  }

  @Test
  void passesWhenNeitherThinkingNorEffortSetAndReasoningIsNull() {
    assertThatCode(() -> validate(params(null, null, null), null, true)).doesNotThrowAnyException();
    assertThatCode(() -> validate(null, null, true)).doesNotThrowAnyException();
  }

  // --- "thinking set" == mode != null; a null mode is treated as unset -----------------------

  @Test
  void thinkingWithNullModeIsTreatedAsUnsetEvenWithoutReasoningCapabilities() {
    // Modeler filled in a budget but left the mode dropdown blank (Task 2 review note): this must
    // not be treated as "thinking set" and must not trigger rule 1.
    final var params = params(new AnthropicThinking(null, 4096, null), null, null);

    assertThatCode(() -> validate(params, null, true)).doesNotThrowAnyException();
  }

  // --- Rule 2: thinking mode not in the declared thinking-modes ------------------------------

  @Test
  void failsWhenThinkingModeNotInDeclaredThinkingModes() {
    final var params = params(new AnthropicThinking(ThinkingMode.ENABLED, 2048, null), null, null);
    final var reasoning = reasoning(List.of(ThinkingMode.ADAPTIVE), List.of());

    assertThatThrownBy(() -> validate(params, reasoning, true))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
  }

  @Test
  void passesWhenThinkingModeIsInDeclaredThinkingModes() {
    final var params = params(new AnthropicThinking(ThinkingMode.ADAPTIVE, null, null), null, null);
    final var reasoning = reasoning(List.of(ThinkingMode.ADAPTIVE), List.of());

    assertThatCode(() -> validate(params, reasoning, true)).doesNotThrowAnyException();
  }

  // --- Rule 3: mode == ENABLED requires a budget ----------------------------------------------

  @Test
  void failsWhenEnabledThinkingHasNullBudgetTokens() {
    final var params = params(new AnthropicThinking(ThinkingMode.ENABLED, null, null), null, null);
    final var reasoning = reasoning(List.of(ThinkingMode.ENABLED), List.of());

    assertThatThrownBy(() -> validate(params, reasoning, true))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
  }

  @Test
  void passesWhenEnabledThinkingHasBudgetTokens() {
    final var params = params(new AnthropicThinking(ThinkingMode.ENABLED, 2048, null), null, null);
    final var reasoning = reasoning(List.of(ThinkingMode.ENABLED), List.of());

    assertThatCode(() -> validate(params, reasoning, true)).doesNotThrowAnyException();
  }

  @Test
  void passesWhenDisabledThinkingHasNoBudgetTokens() {
    final var params = params(new AnthropicThinking(ThinkingMode.DISABLED, null, null), null, null);
    final var reasoning = reasoning(List.of(ThinkingMode.DISABLED), List.of());

    assertThatCode(() -> validate(params, reasoning, true)).doesNotThrowAnyException();
  }

  @Test
  void passesWhenAdaptiveThinkingHasDisplaySetAndNoBudget() {
    final var params =
        params(
            new AnthropicThinking(ThinkingMode.ADAPTIVE, null, ThinkingDisplay.OMITTED),
            null,
            null);
    final var reasoning = reasoning(List.of(ThinkingMode.ADAPTIVE), List.of());

    assertThatCode(() -> validate(params, reasoning, true)).doesNotThrowAnyException();
  }

  // --- Rule 4: effort (non-CUSTOM) set but the model declares no effort levels ---------------

  @Test
  void failsWhenEffortSetAndDeclaredEffortLevelsAreEmpty() {
    final var params = params(null, AnthropicEffort.HIGH, null);
    final var reasoning = reasoning(List.of(), List.of());

    assertThatThrownBy(() -> validate(params, reasoning, true))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
  }

  // --- Rule 5: effort (non-CUSTOM) not in the declared effort-levels --------------------------

  @Test
  void failsWhenEffortNotInDeclaredEffortLevels() {
    final var params = params(null, AnthropicEffort.XHIGH, null);
    final var reasoning = reasoning(List.of(), List.of(AnthropicEffort.LOW, AnthropicEffort.HIGH));

    assertThatThrownBy(() -> validate(params, reasoning, true))
        .isInstanceOf(ConnectorException.class)
        .extracting(e -> ((ConnectorException) e).getErrorCode())
        .isEqualTo(ERROR_CODE_FAILED_MODEL_CALL);
  }

  @Test
  void passesWhenEffortIsInDeclaredEffortLevels() {
    final var params = params(null, AnthropicEffort.HIGH, null);
    final var reasoning = reasoning(List.of(), List.of(AnthropicEffort.LOW, AnthropicEffort.HIGH));

    assertThatCode(() -> validate(params, reasoning, true)).doesNotThrowAnyException();
  }

  // --- Rule 6: effort == CUSTOM always bypasses effort-level validation ----------------------

  @Test
  void customEffortBypassesEffortLevelValidationEvenWithEmptyDeclaredLevels() {
    final var params = params(null, AnthropicEffort.CUSTOM, "verbose");
    final var reasoning = reasoning(List.of(), List.of());

    assertThatCode(() -> validate(params, reasoning, true)).doesNotThrowAnyException();
  }

  @Test
  void customEffortBypassesRule1EvenWhenTheMatchedModelDeclaresNoReasoningAtAll() {
    // effort == CUSTOM is a full escape hatch: it is sent verbatim and bypasses ALL matrix effort
    // validation, including rule 1 (the "model declares no reasoning at all" gate). A matched model
    // with no `reasoning` block whatsoever therefore accepts a CUSTOM effort — the API decides.
    // (Thinking has no such hatch: a set thinking mode still trips rule 1 — covered separately.)
    final var params = params(null, AnthropicEffort.CUSTOM, "verbose");

    assertThatCode(() -> validate(params, null, true)).doesNotThrowAnyException();
  }

  // --- Unmatched model: pass-through, no validation at all ------------------------------------

  @Test
  void unmatchedModelWithThinkingSetAndNoReasoningPassesThrough() {
    final var params = params(new AnthropicThinking(ThinkingMode.ENABLED, null, null), null, null);

    assertThatCode(() -> validate(params, null, false)).doesNotThrowAnyException();
  }

  @Test
  void unmatchedModelWithUnsupportedEffortPassesThrough() {
    final var params = params(null, AnthropicEffort.XHIGH, null);
    final var reasoning = reasoning(List.of(), List.of(AnthropicEffort.LOW));

    assertThatCode(() -> validate(params, reasoning, false)).doesNotThrowAnyException();
  }

  @Test
  void unmatchedModelWithUnsupportedThinkingModePassesThrough() {
    final var params = params(new AnthropicThinking(ThinkingMode.ENABLED, 2048, null), null, null);
    final var reasoning = reasoning(List.of(ThinkingMode.ADAPTIVE), List.of());

    assertThatCode(() -> validate(params, reasoning, false)).doesNotThrowAnyException();
  }
}
