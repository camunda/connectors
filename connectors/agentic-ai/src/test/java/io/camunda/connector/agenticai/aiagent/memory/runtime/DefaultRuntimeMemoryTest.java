/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.runtime;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultRuntimeMemoryTest {

  private static final List<Message> TEST_MESSAGES = TestMessagesFixture.testMessages();

  private RuntimeMemory memory;

  @BeforeEach
  void setUp() {
    memory = new DefaultRuntimeMemory();
  }

  @Test
  void addsSingleMessages() {
    TEST_MESSAGES.forEach(memory::addMessage);
    assertThat(memory.allMessages()).containsExactlyElementsOf(TEST_MESSAGES);
    assertThat(memory.filteredMessages()).containsExactlyElementsOf(TEST_MESSAGES);
  }

  @Test
  void addsListOfMessages() {
    memory.addMessages(TEST_MESSAGES);
    assertThat(memory.allMessages()).containsExactlyElementsOf(TEST_MESSAGES);
    assertThat(memory.filteredMessages()).containsExactlyElementsOf(TEST_MESSAGES);
  }

  @Test
  void clearsMessages() {
    memory.addMessages(TEST_MESSAGES);
    assertThat(memory.allMessages()).containsExactlyElementsOf(TEST_MESSAGES);
    assertThat(memory.filteredMessages()).containsExactlyElementsOf(TEST_MESSAGES);

    memory.clear();
    assertThat(memory.allMessages()).isEmpty();
    assertThat(memory.filteredMessages()).isEmpty();
  }

  @Test
  void replacesExistingSystemMessage() {
    memory.addMessages(TEST_MESSAGES);

    final var newSystemMessage = systemMessage("New system message");
    memory.addMessage(newSystemMessage);

    assertThat(memory.allMessages()).hasSize(TEST_MESSAGES.size()); // no new message added
    assertThat(memory.allMessages().getFirst()).isEqualTo(newSystemMessage);
    assertThat(memory.allMessages())
        .filteredOn(msg -> msg instanceof SystemMessage)
        .hasSize(1)
        .containsExactly(newSystemMessage);
  }

  @Test
  void doesNotReplaceIdenticalSystemMessage() {
    memory.addMessages(TEST_MESSAGES);

    final var newSystemMessage =
        systemMessage(
            ((TextContent) ((SystemMessage) TEST_MESSAGES.getFirst()).content().getFirst()).text());
    memory.addMessage(newSystemMessage);

    assertThat(memory.allMessages().getFirst())
        .isEqualTo(newSystemMessage)
        .isNotSameAs(newSystemMessage)
        .isSameAs(TEST_MESSAGES.getFirst());
  }

  @Test
  void addsSystemMessageAsFirstMessage() {
    TEST_MESSAGES.stream()
        .filter(msg -> !(msg instanceof SystemMessage))
        .forEach(memory::addMessage);

    assertThat(memory.allMessages()).filteredOn(msg -> msg instanceof SystemMessage).isEmpty();

    final var newSystemMessage = systemMessage("New system message");
    memory.addMessage(newSystemMessage);

    assertThat(memory.allMessages())
        .filteredOn(msg -> msg instanceof SystemMessage)
        .hasSize(1)
        .containsExactly(newSystemMessage);
    assertThat(memory.allMessages().getFirst()).isEqualTo(newSystemMessage);
  }

  @Test
  void returnsLastMessage() {
    memory.addMessages(TEST_MESSAGES);
    assertThat(memory.lastMessage()).isPresent().get().isEqualTo(TEST_MESSAGES.getLast());
  }

  @Test
  void returnsEmptyLastMessageWhenNoMessages() {
    assertThat(memory.lastMessage()).isEmpty();
  }
}
