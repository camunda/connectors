/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AgentInputTest {

  @Test
  void aUserPromptConfigurationWithoutADocumentDeliveryDeliversTheContent() {
    final var input = AgentInput.from(new UserPromptConfiguration("Hello", null, null), List.of());

    assertThat(input.userPrompt().documentDelivery()).isEqualTo(DocumentDelivery.CONTENT);
  }

  @ParameterizedTest
  @EnumSource(DocumentDelivery.class)
  void aUserPromptConfigurationWithADocumentDeliveryKeepsIt(DocumentDelivery delivery) {
    final var input =
        AgentInput.from(new UserPromptConfiguration("Hello", null, delivery), List.of());

    assertThat(input.userPrompt().documentDelivery()).isEqualTo(delivery);
  }
}
