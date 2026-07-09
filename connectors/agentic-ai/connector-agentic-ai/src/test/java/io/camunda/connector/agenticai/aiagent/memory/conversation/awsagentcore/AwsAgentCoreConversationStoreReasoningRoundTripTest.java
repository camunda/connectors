/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStore.BedrockAgentCoreClientFactory;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.AwsAgentCoreMemoryStorageConfiguration;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.paginators.ListEventsIterable;

/**
 * Verifies that {@link ReasoningContent}, including its opaque {@code providerPayload}, survives a
 * store-and-load round trip through the AWS AgentCore conversation store and its bespoke {@link
 * AwsAgentCoreConversationMapper}.
 *
 * <p>This test mirrors {@link AwsAgentCoreConversationStoreTest}'s mocked-client setup (no live AWS
 * calls) and feeds the payload captured from {@code createEvent} back into a mocked {@code
 * listEventsPaginator} response to simulate the load path.
 */
@ExtendWith(MockitoExtension.class)
class AwsAgentCoreConversationStoreReasoningRoundTripTest {

  private static final String MEMORY_ID = "test-memory-id";
  private static final String ACTOR_ID = "test-actor-id";
  private static final String SESSION_ID = "test-session-id";

  @Mock private BedrockAgentCoreClient bedrockClient;
  @Mock private BedrockAgentCoreClientFactory clientFactory;
  @Mock private AgentExecutionContext executionContext;
  @Mock private ListEventsIterable listEventsIterable;

  @Captor private ArgumentCaptor<CreateEventRequest> createEventRequestCaptor;

  private AwsAgentCoreConversationStore store;
  private AwsAgentCoreMemoryStorageConfiguration config;

  @BeforeEach
  void setUp() {
    var authentication =
        new AwsAgentCoreMemoryStorageConfiguration.AwsAgentCoreAuthentication
            .AwsStaticCredentialsAuthentication("test-access-key", "test-secret-key");
    config =
        new AwsAgentCoreMemoryStorageConfiguration(
            "us-east-1", null, authentication, MEMORY_ID, ACTOR_ID);
    when(executionContext.configuration())
        .thenReturn(configWithMemory(new MemoryConfiguration(config, 20)));
    when(clientFactory.createClient(config)).thenReturn(bedrockClient);

    var conversationMapper = new AwsAgentCoreConversationMapper(TestObjectMapperSupplier.INSTANCE);

    store = new AwsAgentCoreConversationStore(clientFactory, conversationMapper);
  }

  private static AgentConfiguration configWithMemory(MemoryConfiguration memory) {
    return new AgentConfiguration(null, "model", "anthropic", null, null, memory, null, null, null);
  }

  @Test
  void reasoningContentSurvivesStoreAndLoad() {
    final var assistant =
        AssistantMessage.builder()
            .content(
                List.of(
                    new ReasoningContent("why", Map.of("signature", "sig-xyz"), null),
                    TextContent.textContent("answer")))
            .build();

    final var agentContext =
        AgentContext.builder()
            .conversation(
                AwsAgentCoreConversationContext.builder(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .build())
            .build();

    mockListEventsResponse(List.of());
    when(bedrockClient.createEvent(any(CreateEventRequest.class)))
        .thenReturn(
            CreateEventResponse.builder().event(Event.builder().eventId("evt-1").build()).build());

    AgentContext updatedAgentContext;
    try (var session = store.createSession(executionContext, agentContext)) {
      session.loadMessages(agentContext);

      List<Message> messages = List.of(assistant);
      var updatedConversation =
          session.storeMessages(agentContext, ConversationStoreRequest.of(messages));
      updatedAgentContext = agentContext.withConversation(updatedConversation);
    }

    // simulate the AgentCore backend by feeding the payload we just "wrote" back on the next load
    verify(bedrockClient).createEvent(createEventRequestCaptor.capture());
    final var createdRequest = createEventRequestCaptor.getValue();

    final var replayedEvent =
        Event.builder()
            .eventTimestamp(Instant.now())
            .payload(createdRequest.payload())
            .metadata(createdRequest.metadata())
            .build();
    mockListEventsResponse(List.of(replayedEvent));

    try (var session = store.createSession(executionContext, updatedAgentContext)) {
      var loadResult = session.loadMessages(updatedAgentContext);

      assertThat(loadResult.messages()).hasSize(1);
      var restored = (AssistantMessage) loadResult.messages().get(0);
      assertThat(restored.content()).containsExactlyElementsOf(assistant.content());

      var restoredReasoning = (ReasoningContent) restored.content().get(0);
      assertThat(restoredReasoning.text()).isEqualTo("why");
      assertThat(restoredReasoning.providerPayload()).isEqualTo(Map.of("signature", "sig-xyz"));
    }
  }

  private void mockListEventsResponse(List<Event> events) {
    final SdkIterable<Event> eventsIterable = events::iterator;
    when(listEventsIterable.events()).thenReturn(eventsIterable);
    when(bedrockClient.listEventsPaginator(any(ListEventsRequest.class)))
        .thenReturn(listEventsIterable);
  }
}
