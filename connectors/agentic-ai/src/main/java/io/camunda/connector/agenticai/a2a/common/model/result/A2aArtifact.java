/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.common.model.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.content.Content;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = A2aArtifact.A2aArtifactJacksonProxyBuilder.class)
public record A2aArtifact(
    String artifactId,
    @Nullable String name,
    @Nullable String description,
    List<Content> contents,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata) {

  public static A2aArtifactBuilder builder() {
    return A2aArtifactBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class A2aArtifactJacksonProxyBuilder extends A2aArtifactBuilder {}
}
