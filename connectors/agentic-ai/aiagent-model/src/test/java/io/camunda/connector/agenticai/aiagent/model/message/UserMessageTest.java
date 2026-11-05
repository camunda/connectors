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
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserMessageTest {

  private static AgentDocumentContent block(String id, DocumentDelivery delivery) {
    return new AgentDocumentContent(AgentDocumentId.of(id), delivery, null);
  }

  @Test
  void documentIdsWithContentCollectsTheBlocksWithTheDeliveryContent() {
    final var message =
        UserMessage.builder()
            .content(
                List.of(
                    TextContent.textContent("see"),
                    block("doc-1", DocumentDelivery.CONTENT),
                    block("doc-2", DocumentDelivery.REFERENCE),
                    ObjectContent.objectContent(Map.of("k", "v")),
                    block("doc-1", DocumentDelivery.CONTENT)))
            .build();

    assertThat(message.documentIdsWithContent()).containsExactly(AgentDocumentId.of("doc-1"));
  }

  @Test
  void documentIdsWithContentIsEmptyWithoutBlocksWithContent() {
    final var message =
        UserMessage.builder()
            .content(
                List.of(
                    TextContent.textContent("hello"), block("doc-2", DocumentDelivery.REFERENCE)))
            .build();

    assertThat(message.documentIdsWithContent()).isEmpty();
  }
}
