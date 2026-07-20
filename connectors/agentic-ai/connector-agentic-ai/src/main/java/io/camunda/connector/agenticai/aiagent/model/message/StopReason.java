/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Provider-neutral, normalized finish reason. Diagnostics + a thin predicate surface only — never
 * load-bearing for control flow (that keys off {@link AssistantMessage#hasToolCalls()}). The raw
 * vendor value is always preserved in {@link AssistantMessage#metadata()} in addition to living on
 * this field for a genuinely unrecognised value (see {@link UnknownStopReason} below).
 *
 * <p>This is a sealed interface, not an enum: {@link KnownStopReason} covers the recognised values,
 * while {@link UnknownStopReason} carries a vendor stop reason verbatim when it doesn't map to any
 * of them. Consumers must handle the {@link UnknownStopReason} case and must not assume a closed
 * set of values — new known values may be added over time, and unrecognised vendor values are
 * expected and non-breaking. It is part of the persisted message contract, so serialization (a bare
 * JSON string, see {@link #value()}) must remain backward compatible.
 *
 * <p>Continuation states (e.g. Anthropic {@code pause_turn}) are NOT represented here — see the
 * {@code Continuation} chat result. See ADR 009 §3.
 */
public sealed interface StopReason
    permits StopReason.KnownStopReason, StopReason.UnknownStopReason {

  StopReason STOP = KnownStopReason.STOP;
  StopReason LENGTH = KnownStopReason.LENGTH;
  StopReason TOOL_USE = KnownStopReason.TOOL_USE;
  StopReason CONTENT_FILTERED = KnownStopReason.CONTENT_FILTERED;
  StopReason GUARDRAIL = KnownStopReason.GUARDRAIL;
  StopReason ERROR = KnownStopReason.ERROR;
  StopReason ABORTED = KnownStopReason.ABORTED;

  /** The wire value: a known constant's name, or the verbatim vendor string when unrecognised. */
  @JsonValue
  String value();

  /**
   * Resolves a wire value to a {@link KnownStopReason} constant, falling back to an {@link
   * UnknownStopReason} carrying the value verbatim when it doesn't match a known constant.
   */
  @JsonCreator
  static StopReason of(String value) {
    try {
      return KnownStopReason.valueOf(value);
    } catch (IllegalArgumentException e) {
      return new UnknownStopReason(value);
    }
  }

  /** The set of recognised, normalized finish reasons. */
  enum KnownStopReason implements StopReason {
    STOP,
    LENGTH,
    TOOL_USE,
    CONTENT_FILTERED,
    GUARDRAIL,
    ERROR,
    ABORTED;

    @Override
    public String value() {
      return name();
    }
  }

  /** A vendor stop reason that doesn't map to any {@link KnownStopReason}, carried verbatim. */
  record UnknownStopReason(String value) implements StopReason {}
}
