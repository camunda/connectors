/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.model.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.content.Content;
import java.util.Map;
import javax.annotation.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = A2aContent.A2aContentJacksonProxyBuilder.class)
public record A2aContent(
    Content content,
    @Nullable @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata) {

  public static A2aContentBuilder builder() {
    return new A2aContentBuilder();
  }

  public static class A2aContentJacksonProxyBuilder extends A2aContentBuilder {}
}
