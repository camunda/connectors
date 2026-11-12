/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatAssistantMessageTest {

  @Test
  void keepsItsMetadataWhenTheOriginalMapChanges() {
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("key", "value");
    metadata.put("empty", null);

    final var message = new ChatAssistantMessage(List.of(), List.of(), null, metadata);
    metadata.put("key", "changed");

    assertThat(message.metadata()).containsEntry("key", "value").containsEntry("empty", null);
    assertThatThrownBy(() -> message.metadata().put("other", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void hasEmptyMetadataWhenItHasNone() {
    assertThat(new ChatAssistantMessage(List.of(), List.of(), null, null).metadata()).isEmpty();
  }
}
