/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.common.annotation.AgenticAiRecord;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = UserMessage.UserMessageJacksonProxyBuilder.class)
public record UserMessage(
    @RecordBuilder.Initializer(source = MessageUtil.class, value = "generateId") MessageId id,
    @Nullable String name,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Content> content,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements UserMessageBuilder.With, Message, ContentMessage {

  /**
   * Metadata key identifying a user message as containing documents extracted from tool call
   * results. For an agent instance that tracks its documents, this is the message that follows the
   * tool results with the documents whose content a tool asked for, and it holds only content that
   * passed the content guard.
   */
  public static final String METADATA_TOOL_CALL_DOCUMENTS = "toolCallDocuments";

  /**
   * The ids of the documents whose content the model reads in this message: the documents of the
   * blocks with the delivery {@code content}.
   */
  public Set<AgentDocumentId> documentIdsWithContent() {
    return content.stream()
        .filter(AgentDocumentContent.class::isInstance)
        .map(AgentDocumentContent.class::cast)
        .filter(reference -> reference.delivery() == DocumentDelivery.CONTENT)
        .map(AgentDocumentContent::id)
        .collect(Collectors.toUnmodifiableSet());
  }

  public static UserMessageBuilder builder() {
    return UserMessageBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class UserMessageJacksonProxyBuilder extends UserMessageBuilder {}
}
