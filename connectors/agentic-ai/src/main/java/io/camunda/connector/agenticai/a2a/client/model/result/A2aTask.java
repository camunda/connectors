/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.model.result;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.util.List;
import java.util.Map;

@AgenticAiRecord
@JsonDeserialize(builder = A2aTask.A2aTaskJacksonProxyBuilder.class)
public record A2aTask(
    String id,
    String contextId,
    A2aTaskStatus status,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<A2aArtifact> artifacts,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<A2aMessage> history)
    implements A2aSendMessageResult, A2aTaskBuilder.With {

  public static final String TASK = "task";

  @JsonGetter
  public String kind() {
    return TASK;
  }

  public static A2aTaskBuilder builder() {
    return A2aTaskBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class A2aTaskJacksonProxyBuilder extends A2aTaskBuilder {}
}
