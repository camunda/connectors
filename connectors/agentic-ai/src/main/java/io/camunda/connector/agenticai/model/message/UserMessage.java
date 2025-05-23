/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.content.Content;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = UserMessage.UserMessageJacksonProxyBuilder.class)
public record UserMessage(
    @Nullable String name,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Content> content,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements UserMessageBuilder.With, Message, ContentMessage {

  public static UserMessage userMessage(String text) {
    return builder().content(List.of(textContent(text))).build();
  }

  public static UserMessage userMessage(List<Content> content) {
    return builder().content(content).build();
  }

  public static UserMessageBuilder builder() {
    return UserMessageBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class UserMessageJacksonProxyBuilder extends UserMessageBuilder {}
}
