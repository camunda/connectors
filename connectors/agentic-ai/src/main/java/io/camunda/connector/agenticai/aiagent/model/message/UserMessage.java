/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.model.message.content.ContentBlock;
import io.camunda.connector.agenticai.model.AgenticAiRecordBuilder;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

@AgenticAiRecordBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = UserMessage.UserMessageJacksonProxyBuilder.class)
public record UserMessage(
    @Nullable String name,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ContentBlock> content,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements UserMessageBuilder.With, Message, ContentMessage {

  @Override
  public MessageRole role() {
    return MessageRole.USER;
  }

  public static UserMessageBuilder builder() {
    return UserMessageBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class UserMessageJacksonProxyBuilder extends UserMessageBuilder {}
}
