/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageUtilTest {

  private static AgentDocumentContent block(String id, DocumentDelivery delivery) {
    return new AgentDocumentContent(AgentDocumentId.of(id), delivery, null);
  }

  @Test
  void documentIdsWithContentCollectsTheBlocksWithTheDeliveryContentOfUserMessagesOnly() {
    final var loaded = block("doc-1", DocumentDelivery.CONTENT);
    final var referenced = block("doc-2", DocumentDelivery.REFERENCE);

    final var ids =
        MessageUtil.documentIdsWithContent(
            List.of(
                UserMessage.builder().content(List.of(loaded, referenced)).build(),
                AssistantMessage.builder()
                    .content(List.of(block("doc-3", DocumentDelivery.CONTENT)))
                    .build()));

    assertThat(ids).containsExactly(AgentDocumentId.of("doc-1"));
  }
}
