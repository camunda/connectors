/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.LinkedHashMap;
import org.jspecify.annotations.Nullable;

@AgenticAiRecord
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonDeserialize(builder = AgentSubProcessResponse.AgentSubProcessResponseJacksonProxyBuilder.class)
public record AgentSubProcessResponse(
    @Nullable AgentContext context,
    @Nullable AssistantMessage responseMessage,
    @Nullable String responseText,
    @Nullable Object responseJson,
    // the response documents by key, in call order; a LinkedHashMap because the record builder
    // copies a Map into an immutable map of unspecified iteration order
    @RecordBuilder.Initializer(
            source = AgentSubProcessResponse.class,
            value = "noResponseDocuments")
        LinkedHashMap<String, ReturnedDocument> responseDocuments)
    implements AgentSubProcessResponseBuilder.With {

  static LinkedHashMap<String, ReturnedDocument> noResponseDocuments() {
    return new LinkedHashMap<>();
  }

  public static AgentSubProcessResponseBuilder builder() {
    return AgentSubProcessResponseBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AgentSubProcessResponseJacksonProxyBuilder
      extends AgentSubProcessResponseBuilder {}
}
