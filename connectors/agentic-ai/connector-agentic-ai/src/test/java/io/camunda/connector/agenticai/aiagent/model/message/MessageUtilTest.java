/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageUtilTest {

  private static DocumentReferenceContent reference(String id, DocumentDelivery delivery) {
    return new DocumentReferenceContent(id, null, null, null, "user_message", null, null, delivery);
  }

  @Test
  void documentIdsWithContentCollectsTheReferencesWithTheDeliveryContentOfUserMessagesOnly() {
    final var loaded = reference("doc-1", DocumentDelivery.CONTENT);
    final var referenced = reference("doc-2", DocumentDelivery.REFERENCE);

    final var ids =
        MessageUtil.documentIdsWithContent(
            List.of(
                UserMessage.builder().content(List.of(loaded, referenced)).build(),
                AssistantMessage.builder()
                    .content(List.of(reference("doc-3", DocumentDelivery.CONTENT)))
                    .build()));

    assertThat(ids).containsExactly("doc-1");
  }
}
