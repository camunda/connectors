/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

import io.camunda.connector.agenticai.aiagent.model.message.content.PortableContent;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The result of one tool call in a chat request.
 *
 * @param id the id of the tool call
 * @param name the name of the tool
 * @param content the content blocks of the result: text, documents and objects
 */
public record ChatToolCallResult(
    @Nullable String id, @Nullable String name, List<PortableContent> content) {

  public ChatToolCallResult {
    content = List.copyOf(content);
  }
}
