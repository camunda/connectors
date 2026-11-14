/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.conversation.AgentInput.UserPrompt;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.api.document.DocumentCreationRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentInputTest {

  @Test
  void aUserPromptWithATextHasContent() {
    assertThat(new UserPrompt("Hello", List.of(), UserPromptDocumentDelivery.UNSET).hasContent())
        .isTrue();
  }

  @Test
  void aUserPromptWithADocumentOnlyHasContent() {
    final var document =
        TestDocuments.documentFactory()
            .create(DocumentCreationRequest.from("content".getBytes()).fileName("a.txt").build());

    assertThat(
            new UserPrompt(null, List.of(document), UserPromptDocumentDelivery.REFERENCE)
                .hasContent())
        .isTrue();
  }

  @Test
  void aUserPromptWithABlankTextAndNoDocumentHasNoContent() {
    assertThat(new UserPrompt(null, List.of(), UserPromptDocumentDelivery.UNSET).hasContent())
        .isFalse();
    assertThat(new UserPrompt("", List.of(), UserPromptDocumentDelivery.UNSET).hasContent())
        .isFalse();
    assertThat(new UserPrompt("  ", List.of(), UserPromptDocumentDelivery.CONTENT).hasContent())
        .isFalse();
  }
}
