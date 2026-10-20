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
 * Provider-neutral, normalized finish reason.
 *
 * <p>This is a sealed interface, not an enum: {@link KnownStopReason} covers the recognised values,
 * while {@link UnknownStopReason} carries a vendor stop reason verbatim when it doesn't map to any
 * of them. Consumers must handle the {@link UnknownStopReason} case and must not assume a closed
 * set of values — new known values may be added over time, and unrecognised vendor values are
 * expected and non-breaking. It is part of the persisted message contract, so serialization (a bare
 * JSON string, see {@link #value()}) must remain backward compatible.
 *
 * <p>Continuation states are NOT represented here — see the {@code ChatResult.Continuation} chat
 * result. Terminal failure conditions a provider recognizes are NOT represented here either: since
 * how a provider signals one varies (an HTTP-level error vs. a normal stop/finish reason value), it
 * throws {@code ChatModelRejectedException} directly rather than returning it as a finish reason.
 */
public sealed interface StopReason
    permits StopReason.KnownStopReason, StopReason.UnknownStopReason {

  StopReason STOP = KnownStopReason.STOP;
  StopReason LENGTH = KnownStopReason.LENGTH;
  StopReason TOOL_USE = KnownStopReason.TOOL_USE;

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
    /** The model finished the turn naturally, with nothing further to produce. */
    STOP,
    /** The response was truncated because a token/length limit was reached. */
    LENGTH,
    /** The model stopped to invoke one or more tools. */
    TOOL_USE;

    @Override
    public String value() {
      return name();
    }
  }

  /** A vendor stop reason that doesn't map to any {@link KnownStopReason}, carried verbatim. */
  record UnknownStopReason(String value) implements StopReason {}
}
