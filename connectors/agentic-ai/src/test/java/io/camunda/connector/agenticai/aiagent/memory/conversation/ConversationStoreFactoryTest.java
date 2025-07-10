/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentJobContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.InProcessMemoryStorageConfiguration;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.store.CamundaDocumentStore;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationStoreFactoryTest {

  private final AgentContext agentContext = AgentContext.empty();

  @Mock private ObjectMapper objectMapper;
  @Mock private DocumentFactory documentFactory;
  @Mock private CamundaDocumentStore camundaDocumentStore;
  @Mock private AgentJobContext agentJobContext;

  @InjectMocks private ConversationStoreFactoryImpl factory;

  @Test
  void createsInProcessConversationStore() {
    try (final var constructor = mockConstruction(InProcessConversationStore.class)) {
      final var store =
          factory.createConversationStore(
              new AgentExecutionContext(
                  agentJobContext, agentRequest(new InProcessMemoryStorageConfiguration())),
              agentContext);

      assertThat(store).isInstanceOf(InProcessConversationStore.class);
      assertThat(constructor.constructed()).hasSize(1).first().isEqualTo(store);
    }
  }

  @Test
  void fallsbackToInProcessConversationStoreWhenNotConfigured() {
    try (final var constructor = mockConstruction(InProcessConversationStore.class)) {
      final var store =
          factory.createConversationStore(
              new AgentExecutionContext(agentJobContext, agentRequest(null)), agentContext);

      assertThat(store).isInstanceOf(InProcessConversationStore.class);
      assertThat(constructor.constructed()).hasSize(1).first().isEqualTo(store);
    }
  }

  @Test
  void createsCamundaDocumentConversationStore() {
    try (final var constructor = mockConstruction(CamundaDocumentConversationStore.class)) {
      final var store =
          factory.createConversationStore(
              new AgentExecutionContext(
                  agentJobContext,
                  agentRequest(
                      new CamundaDocumentMemoryStorageConfiguration(
                          Duration.ofHours(1), Map.of("customKey", "customValue")))),
              agentContext);

      assertThat(store).isInstanceOf(CamundaDocumentConversationStore.class);
      assertThat(constructor.constructed()).hasSize(1).first().isEqualTo(store);
    }
  }

  private AgentRequest agentRequest(MemoryStorageConfiguration storageConfig) {
    return new AgentRequest(
        null,
        new AgentRequestData(
            null, null, null, null, new MemoryConfiguration(storageConfig, 10), null, null));
  }
}
