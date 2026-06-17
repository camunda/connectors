/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.runtime;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.*;
import static io.camunda.connector.agenticai.model.message.MessageUtil.singleTextContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.UserMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageWindowFilterTest {

  @Test
  void throwsWhenMaxMessagesNegative() {
    List<Message> messages = List.of(userMessage("hi"));
    assertThatThrownBy(() -> MessageWindowFilter.apply(messages, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void returnsAllMessages_whenCountWithinWindow() {
    List<Message> messages = List.of(userMessage("hi"), assistantMessage("hello"));
    assertThat(MessageWindowFilter.apply(messages, 10)).isEqualTo(messages);
  }

  @Test
  void preservesSystemMessage_whenEvicting() {
    var sys = systemMessage("sys");
    var u1 = userMessage("1");
    var a1 = assistantMessage("a1");
    var u2 = userMessage("2");
    var a2 = assistantMessage("a2");
    var u3 = userMessage("3");
    // window=4 → evicts u1+a1, keeps sys+u2+a2+u3 (system message counted toward limit)
    var result = MessageWindowFilter.apply(List.of(sys, u1, a1, u2, a2, u3), 4);
    assertThat(result).containsExactly(sys, u2, a2, u3);
  }

  @Test
  void evictsToolCallResults_whenAssistantMessageEvicted() {
    var u1 = userMessage("hi");
    var a1 = assistantMessage("thinking", TOOL_CALLS);
    var tcr = toolCallResultMessage(TOOL_CALL_RESULTS);
    var a2 = assistantMessage("done");
    // window=2 → evicts u1+a1; tool call result message orphaned, must also be evicted
    var result = MessageWindowFilter.apply(List.of(u1, a1, tcr, a2), 2);
    assertThat(result).containsExactly(a2);
  }

  @Test
  void evictsDocumentMessages_whenToolCallResultEvicted() {
    var u1 = userMessage("hi");
    var a1 = assistantMessage("thinking", TOOL_CALLS);
    var tcr = toolCallResultMessage(TOOL_CALL_RESULTS);
    var docMsg =
        UserMessage.builder()
            .content(singleTextContent("doc"))
            .metadata(Map.of(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true))
            .build();
    var a2 = assistantMessage("done");
    var result = MessageWindowFilter.apply(List.of(u1, a1, tcr, docMsg, a2), 2);
    assertThat(result).containsExactly(a2);
  }
}
