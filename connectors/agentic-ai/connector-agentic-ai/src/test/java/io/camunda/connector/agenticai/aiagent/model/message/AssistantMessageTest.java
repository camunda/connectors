/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantMessageTest {

  @Test
  void carriesStopReasonAndModelIdentityAndRoundTrips() throws Exception {
    var mapper = new ObjectMapper();
    var msg =
        AssistantMessage.builder()
            .content(List.of(TextContent.textContent("hi")))
            .modelId("claude-opus-4-8")
            .messageId("msg_123")
            .stopReason(StopReason.STOP)
            .metadata(Map.of("rawStopReason", "end_turn"))
            .build();

    var restored = mapper.readValue(mapper.writeValueAsString(msg), AssistantMessage.class);

    assertThat(restored.modelId()).isEqualTo("claude-opus-4-8");
    assertThat(restored.messageId()).isEqualTo("msg_123");
    assertThat(restored.stopReason()).isEqualTo(StopReason.STOP);
    assertThat(restored.metadata()).containsEntry("rawStopReason", "end_turn");
    assertThat(restored.hasToolCalls()).isFalse();
  }
}
