/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationStoreRegistryTest {

  private final AgentContext agentContext = AgentContext.empty();

  @Mock private ObjectMapper objectMapper;
  @Mock private DocumentFactory documentFactory;
  @Mock private CamundaDocumentStore documentStore;
  @Mock private AgentJobContext agentJobContext;

  private InProcessConversationStore inProcessConversationStore;
  private CamundaDocumentConversationStore camundaDocumentConversationStore;

  private ConversationStoreRegistry registry;

  @BeforeEach
  void setUp() {
    inProcessConversationStore = new InProcessConversationStore();
    camundaDocumentConversationStore =
        new CamundaDocumentConversationStore(documentFactory, documentStore, objectMapper);

    registry =
        new ConversationStoreRegistryImpl(
            List.of(inProcessConversationStore, camundaDocumentConversationStore));
  }

  @Test
  void throwsExceptionWhenRegisteringDuplicateStoreViaConstructor() {
    assertThatThrownBy(
            () ->
                new ConversationStoreRegistryImpl(
                    List.of(inProcessConversationStore, new InProcessConversationStore())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conversation store with type 'in-process' is already registered.");
  }

  @Test
  void throwsExceptionWhenRegisteringDuplicateStoreViaRegisterMethod() {
    final var registry = new ConversationStoreRegistryImpl();
    registry.registerConversationStore(inProcessConversationStore);

    assertThatThrownBy(() -> registry.registerConversationStore(new InProcessConversationStore()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conversation store with type 'in-process' is already registered.");
  }

  @Test
  void throwsExceptionWhenRegisteringDuplicateStoreViaAdditionalRegisterMethod() {
    final var registry = new ConversationStoreRegistryImpl(List.of(inProcessConversationStore));

    assertThatThrownBy(() -> registry.registerConversationStore(new InProcessConversationStore()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conversation store with type 'in-process' is already registered.");
  }

  @Test
  void createsInProcessConversationStore() {
    final var store =
        registry.getConversationStore(
            new AgentExecutionContext(
                agentJobContext, agentRequest(new InProcessMemoryStorageConfiguration())),
            agentContext);

    assertThat(store).isEqualTo(inProcessConversationStore);
  }

  @Test
  void fallsBackToInProcessConversationStoreWhenNotConfigured() {
    final var store =
        registry.getConversationStore(
            new AgentExecutionContext(agentJobContext, agentRequest(null)), agentContext);

    assertThat(store).isEqualTo(inProcessConversationStore);
  }

  @Test
  void createsCamundaDocumentConversationStore() {
    final var store =
        registry.getConversationStore(
            new AgentExecutionContext(
                agentJobContext,
                agentRequest(
                    new CamundaDocumentMemoryStorageConfiguration(
                        Duration.ofHours(1), Map.of("customKey", "customValue")))),
            agentContext);

    assertThat(store).isEqualTo(camundaDocumentConversationStore);
  }

  @Test
  void throwsExceptionWhenNoStoreRegisteredForStorageType() {
    final var registry = new ConversationStoreRegistryImpl();
    assertThatThrownBy(
            () ->
                registry.getConversationStore(
                    new AgentExecutionContext(
                        agentJobContext, agentRequest(new InProcessMemoryStorageConfiguration())),
                    agentContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "No conversation store registered for storage configuration type: in-process");
  }

  private AgentRequest agentRequest(MemoryStorageConfiguration storageConfig) {
    return new AgentRequest(
        null,
        new AgentRequestData(
            null, null, null, null, new MemoryConfiguration(storageConfig, 10), null, null));
  }
}
