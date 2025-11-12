/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.common.model.result;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.content.Content;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = A2aMessage.A2aMessageJacksonProxyBuilder.class)
public record A2aMessage(
    Role role,
    String messageId,
    String contextId,
    @Nullable String taskId,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> referenceTaskIds,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata,
    List<Content> contents)
    implements A2aSendMessageResult {

  public static final String MESSAGE = "message";

  @Override
  @JsonGetter
  public String kind() {
    return MESSAGE;
  }

  public enum Role {
    USER("user"),
    AGENT("agent");

    private final String role;

    Role(String role) {
      this.role = role;
    }

    @JsonValue
    public String asString() {
      return this.role;
    }
  }

  public static A2aMessageBuilder builder() {
    return A2aMessageBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class A2aMessageJacksonProxyBuilder extends A2aMessageBuilder {}
}
