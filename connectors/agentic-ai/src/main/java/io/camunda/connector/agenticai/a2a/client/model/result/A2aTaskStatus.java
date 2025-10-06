/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.model.result;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.time.OffsetDateTime;
import javax.annotation.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = A2aTaskStatus.A2aTaskStatusJacksonProxyBuilder.class)
public record A2aTaskStatus(
    TaskState state, @Nullable A2aMessage message, @Nullable OffsetDateTime timestamp) {

  public enum TaskState {
    SUBMITTED("submitted"),
    WORKING("working"),
    INPUT_REQUIRED("input-required"),
    AUTH_REQUIRED("auth-required"),
    COMPLETED("completed"),
    CANCELED("canceled"),
    FAILED("failed"),
    REJECTED("rejected"),
    UNKNOWN("unknown");

    private final String state;

    TaskState(String state) {
      this.state = state;
    }

    @JsonValue
    public String asString() {
      return state;
    }

    public boolean isSubmittedOrWorking() {
      return this == SUBMITTED || this == WORKING;
    }

    public boolean isCompleted() {
      return this == COMPLETED;
    }

    @JsonCreator
    public static TaskState fromString(String stateStr) {
      for (TaskState taskState : TaskState.values()) {
        if (taskState.state.equals(stateStr)) {
          return taskState;
        }
      }
      throw new IllegalArgumentException("Unknown state: " + stateStr);
    }
  }

  public static A2aTaskStatusBuilder builder() {
    return A2aTaskStatusBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class A2aTaskStatusJacksonProxyBuilder extends A2aTaskStatusBuilder {}
}
