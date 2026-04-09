/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStore.BedrockAgentCoreClientFactory;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper;
import io.camunda.connector.agenticai.aiagent.memory.runtime.DefaultRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.AwsAgentCoreMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.InProcessMemoryStorageConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.util.TestObjectMapperSupplier;
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
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;
import software.amazon.awssdk.services.bedrockagentcore.paginators.ListEventsIterable;

@ExtendWith(MockitoExtension.class)
class AwsAgentCoreConversationStoreTest {

  private static final String MEMORY_ID = "test-memory-id";
  private static final String ACTOR_ID = "test-actor-id";
  private static final String SESSION_ID = "test-session-id";

  @Mock private BedrockAgentCoreClient bedrockClient;
  @Mock private BedrockAgentCoreClientFactory clientFactory;
  @Mock private AgentExecutionContext executionContext;
  @Mock private ListEventsIterable listEventsIterable;

  @Captor private ArgumentCaptor<CreateEventRequest> createEventRequestCaptor;
  @Captor private ArgumentCaptor<ListEventsRequest> listEventsRequestCaptor;

  private AwsAgentCoreConversationStore store;
  private RuntimeMemory memory;
  private AwsAgentCoreMemoryStorageConfiguration config;

  @BeforeEach
  void setUp() {
    var authentication =
        new AwsAgentCoreMemoryStorageConfiguration.AwsAgentCoreAuthentication
            .AwsStaticCredentialsAuthentication("test-access-key", "test-secret-key");
    config =
        new AwsAgentCoreMemoryStorageConfiguration(null, null, authentication, MEMORY_ID, ACTOR_ID);
    lenient().when(executionContext.memory()).thenReturn(new MemoryConfiguration(config, 20));
    lenient().when(clientFactory.createClient(config)).thenReturn(bedrockClient);

    var mapper = new AwsAgentCoreConversationMapper(TestObjectMapperSupplier.INSTANCE);

    store = new AwsAgentCoreConversationStore(clientFactory, mapper);
    memory = new DefaultRuntimeMemory();
  }

  @Test
  void storeTypeIsAlignedWithConfiguration() {
    assertThat(store.type()).isEqualTo(config.storeType()).isEqualTo("aws-agentcore");
  }

  @Test
  void throwsExceptionForMissingConfiguration() {
    final var agentContext = AgentContext.empty();
    when(executionContext.memory()).thenReturn(new MemoryConfiguration(null, 20));

    assertThatThrownBy(
            () ->
                store.executeInSession(
                    executionContext,
                    agentContext,
                    session -> {
                      session.loadIntoRuntimeMemory(agentContext, memory);
                      return agentResponse(agentContext);
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Expected memory storage configuration to be of type AwsAgentCoreMemoryStorageConfiguration, but got: null");
  }

  @Test
  void throwsExceptionForUnsupportedConfiguration() {
    final var agentContext = AgentContext.empty();
    when(executionContext.memory())
        .thenReturn(new MemoryConfiguration(new InProcessMemoryStorageConfiguration(), 20));

    assertThatThrownBy(
            () ->
                store.executeInSession(
                    executionContext,
                    agentContext,
                    session -> {
                      session.loadIntoRuntimeMemory(agentContext, memory);
                      return agentResponse(agentContext);
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(
            "Expected memory storage configuration to be of type AwsAgentCoreMemoryStorageConfiguration, but got:")
        .hasMessageContaining("InProcessMemoryStorageConfiguration");
  }

  @Test
  void loadsMessagesFromAgentCoreMemory() {
    final var agentContext =
        AgentContext.builder()
            .conversation(
                AwsAgentCoreConversationContext.builder()
                    .conversationId(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .sessionId(SESSION_ID)
                    .branchName("prev-branch")
                    .lastEventId("evt-2")
                    .build())
            .build();

    // Mock list events response with user and assistant messages
    final var events =
        List.of(
            createEvent(Role.USER, "Hello!", Instant.now().minusSeconds(60)),
            createEvent(Role.ASSISTANT, "Hi there!", Instant.now().minusSeconds(30)));
    mockListEventsResponse(events);

    store.executeInSession(
        executionContext,
        agentContext,
        session -> {
          session.loadIntoRuntimeMemory(agentContext, memory);
          return agentResponse(agentContext);
        });

    // Verify messages were loaded
    assertThat(memory.allMessages()).hasSize(2);
    verify(bedrockClient).listEventsPaginator(listEventsRequestCaptor.capture());
    final var request = listEventsRequestCaptor.getValue();
    assertThat(request.memoryId()).isEqualTo(MEMORY_ID);
    assertThat(request.actorId()).isEqualTo(ACTOR_ID);
    assertThat(request.sessionId()).isEqualTo(SESSION_ID);
    assertThat(request.includePayloads()).isTrue();
  }

  @Test
  void storesNewMessagesToAgentCoreMemory() {
    final var agentContext =
        AgentContext.builder()
            .conversation(
                AwsAgentCoreConversationContext.builder()
                    .conversationId(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .sessionId(SESSION_ID)
                    .build())
            .build();

    // Mock empty list events response for new conversation
    mockListEventsResponse(List.of());
    when(bedrockClient.createEvent(any(CreateEventRequest.class)))
        .thenReturn(createEventResponse());

    final var result =
        store.executeInSession(
            executionContext,
            agentContext,
            session -> {
              session.loadIntoRuntimeMemory(agentContext, memory);
              // Add messages to memory
              memory.addMessage(userMessage("Hello!"));
              memory.addMessage(assistantMessage("Hi there!"));
              return agentResponse(session.storeFromRuntimeMemory(agentContext, memory));
            });

    // Verify messages were stored
    verify(bedrockClient, times(2)).createEvent(createEventRequestCaptor.capture());
    final var requests = createEventRequestCaptor.getAllValues();

    assertThat(requests).hasSize(2);
    assertThat(requests.get(0).memoryId()).isEqualTo(MEMORY_ID);
    assertThat(requests.get(0).actorId()).isEqualTo(ACTOR_ID);
    // First turn uses "main" as clientToken prefix (no branch yet)
    assertThat(requests.get(0).clientToken()).isEqualTo("main:0");
    assertThat(requests.get(1).clientToken()).isEqualTo("main:1");
    // First turn has no previous event to branch from, so no branch field
    assertThat(requests.get(0).branch()).isNull();

    // Verify conversation context was updated
    final var conversation = result.context().conversation();
    assertThat(conversation).isInstanceOf(AwsAgentCoreConversationContext.class);
    final var agentCoreContext = (AwsAgentCoreConversationContext) conversation;
    assertThat(agentCoreContext.memoryId()).isEqualTo(MEMORY_ID);
    assertThat(agentCoreContext.actorId()).isEqualTo(ACTOR_ID);
    assertThat(agentCoreContext.lastEventId()).isNotNull();
    // First turn writes to main timeline, no branch created
    assertThat(agentCoreContext.branchName()).isNull();
  }

  @Test
  void incrementallyStoresOnlyNewMessages() {
    // Start with existing context that has 2 stored messages
    final var agentContext =
        AgentContext.builder()
            .conversation(
                AwsAgentCoreConversationContext.builder()
                    .conversationId(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .sessionId(SESSION_ID)
                    .branchName("prev-branch")
                    .lastEventId("evt-2")
                    .build())
            .build();

    // Mock loading 2 existing messages
    final var events =
        List.of(
            createEvent(Role.USER, "Hello!", Instant.now().minusSeconds(60)),
            createEvent(Role.ASSISTANT, "Hi there!", Instant.now().minusSeconds(30)));
    mockListEventsResponse(events);
    when(bedrockClient.createEvent(any(CreateEventRequest.class)))
        .thenReturn(createEventResponse());

    store.executeInSession(
        executionContext,
        agentContext,
        session -> {
          session.loadIntoRuntimeMemory(agentContext, memory);
          // Add one new message
          memory.addMessage(userMessage("What's the weather?"));
          return agentResponse(session.storeFromRuntimeMemory(agentContext, memory));
        });

    // Verify only 1 new message was stored (not the existing 2)
    verify(bedrockClient, times(1)).createEvent(createEventRequestCaptor.capture());
    final var request = createEventRequestCaptor.getValue();
    // New messages written to a new branch forked from the previous turn's last event
    assertThat(request.branch()).isNotNull();
    assertThat(request.branch().rootEventId()).isEqualTo("evt-2");
    assertThat(request.clientToken()).endsWith(":0");
  }

  @Test
  void handlesEmptyConversation() {
    final var agentContext = AgentContext.empty();

    // Mock empty list events response
    mockListEventsResponse(List.of());

    final var result =
        store.executeInSession(
            executionContext,
            agentContext,
            session -> {
              session.loadIntoRuntimeMemory(agentContext, memory);
              return agentResponse(session.storeFromRuntimeMemory(agentContext, memory));
            });

    assertThat(memory.allMessages()).isEmpty();

    // Verify conversation context was created
    final var conversation = result.context().conversation();
    assertThat(conversation).isInstanceOf(AwsAgentCoreConversationContext.class);
  }

  @Test
  void usesStaticCredentialsAuthentication() {
    // Verify that the client factory is called with the correct configuration including auth
    final var agentContext = AgentContext.empty();
    mockListEventsResponse(List.of());

    store.executeInSession(
        executionContext,
        agentContext,
        session -> {
          session.loadIntoRuntimeMemory(agentContext, memory);
          return agentResponse(agentContext);
        });

    // Verify client factory was called with config including authentication
    verify(clientFactory).createClient(config);
    assertThat(config.authentication())
        .isInstanceOf(
            AwsAgentCoreMemoryStorageConfiguration.AwsAgentCoreAuthentication
                .AwsStaticCredentialsAuthentication.class);
  }

  @Test
  void skipsSystemMessagesWhenStoring() {
    final var agentContext =
        AgentContext.builder()
            .conversation(
                AwsAgentCoreConversationContext.builder()
                    .conversationId(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .sessionId(SESSION_ID)
                    .build())
            .build();

    // Mock empty list events response for new conversation
    mockListEventsResponse(List.of());
    when(bedrockClient.createEvent(any(CreateEventRequest.class)))
        .thenReturn(createEventResponse());

    final var result =
        store.executeInSession(
            executionContext,
            agentContext,
            session -> {
              session.loadIntoRuntimeMemory(agentContext, memory);
              // Add system message (should be skipped from AgentCore), user message, and assistant
              // message
              memory.addMessage(systemMessage("You are a helpful assistant."));
              memory.addMessage(userMessage("Hello!"));
              memory.addMessage(assistantMessage("Hi there!"));
              return agentResponse(session.storeFromRuntimeMemory(agentContext, memory));
            });

    // Verify only 2 messages were stored to AgentCore (user and assistant), system message was
    // skipped
    verify(bedrockClient, times(2)).createEvent(createEventRequestCaptor.capture());
    final var requests = createEventRequestCaptor.getAllValues();
    assertThat(requests).hasSize(2);
    assertThat(requests.get(0).clientToken()).endsWith(":0");
    assertThat(requests.get(1).clientToken()).endsWith(":1");

    // Verify conversation context was updated with branch info
    final var conversation = result.context().conversation();
    assertThat(conversation).isInstanceOf(AwsAgentCoreConversationContext.class);
    final var agentCoreContext = (AwsAgentCoreConversationContext) conversation;
    assertThat(agentCoreContext.lastEventId()).isNotNull();

    // Verify system message is preserved in the context
    assertThat(agentCoreContext.systemMessage()).isNotNull();
    assertThat(agentCoreContext.systemMessage()).isInstanceOf(SystemMessage.class);
  }

  @Test
  void restoresSystemMessagesOnLoad() {
    // Create a previous context with system message
    final var systemMsg = systemMessage("You are a helpful assistant.");
    final var previousContext =
        AwsAgentCoreConversationContext.builder()
            .conversationId(SESSION_ID)
            .memoryId(MEMORY_ID)
            .actorId(ACTOR_ID)
            .sessionId(SESSION_ID)
            .branchName("prev-branch")
            .lastEventId("evt-2")
            .systemMessage(systemMsg)
            .build();

    final var agentContext = AgentContext.builder().conversation(previousContext).build();

    // Mock loading 2 existing messages (user and assistant)
    final var events =
        List.of(
            createEvent(Role.USER, "Hello!", Instant.now().minusSeconds(60)),
            createEvent(Role.ASSISTANT, "Hi there!", Instant.now().minusSeconds(30)));
    mockListEventsResponse(events);

    store.executeInSession(
        executionContext,
        agentContext,
        session -> {
          session.loadIntoRuntimeMemory(agentContext, memory);
          return agentResponse(agentContext);
        });

    // Verify 3 messages in memory: 1 system (restored) + 2 from AgentCore
    assertThat(memory.allMessages()).hasSize(3);
    assertThat(memory.allMessages().get(0)).isInstanceOf(SystemMessage.class);
    assertThat(memory.allMessages().get(1)).isInstanceOf(UserMessage.class);
    assertThat(memory.allMessages().get(2)).isInstanceOf(AssistantMessage.class);
  }

  @Test
  void storesAssistantMessageWithToolCalls() {
    final var agentContext =
        AgentContext.builder()
            .conversation(
                AwsAgentCoreConversationContext.builder()
                    .conversationId(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .sessionId(SESSION_ID)
                    .build())
            .build();

    mockListEventsResponse(List.of());
    when(bedrockClient.createEvent(any(CreateEventRequest.class)))
        .thenReturn(createEventResponse());

    // Create assistant message with tool calls
    final var toolCall1 =
        ToolCall.builder()
            .id("call_123")
            .name("getWeather")
            .arguments(Map.of("location", "Seattle"))
            .build();
    final var toolCall2 =
        ToolCall.builder().id("call_456").name("getTime").arguments(Map.of()).build();

    store.executeInSession(
        executionContext,
        agentContext,
        session -> {
          session.loadIntoRuntimeMemory(agentContext, memory);
          memory.addMessage(userMessage("What's the weather and time in Seattle?"));
          memory.addMessage(
              assistantMessage(
                  "Let me check the weather and time for you.", List.of(toolCall1, toolCall2)));
          return agentResponse(session.storeFromRuntimeMemory(agentContext, memory));
        });

    // Verify 2 messages were stored
    verify(bedrockClient, times(2)).createEvent(createEventRequestCaptor.capture());
    final var requests = createEventRequestCaptor.getAllValues();
    assertThat(requests).hasSize(2);
    assertThat(requests.get(0).clientToken()).endsWith(":0");
    assertThat(requests.get(1).clientToken()).endsWith(":1");

    // Verify assistant event stores:
    // - conversational payload with plain assistant text
    // - additional blob payload containing tool call JSON envelope
    final var assistantEventPayloads = requests.get(1).payload();
    assertThat(assistantEventPayloads).hasSize(2);

    final var assistantConversational = assistantEventPayloads.get(0).conversational();
    assertThat(assistantConversational).isNotNull();
    assertThat(assistantConversational.role()).isEqualTo(Role.ASSISTANT);
    assertThat(assistantConversational.content().text())
        .isEqualTo("Let me check the weather and time for you.");

    final var blob = assistantEventPayloads.get(1).blob();
    assertThat(blob).isNotNull();
    final var blobJson = blob.asString();
    // New format: blob envelope with blobType discriminator
    assertThat(blobJson).contains("\"blobType\":\"camunda.toolCalls\"");
    assertThat(blobJson).contains("\"version\":1");
    assertThat(blobJson).contains("\"toolCalls\"");
    assertThat(blobJson).contains("\"id\":\"call_123\"");
    assertThat(blobJson).contains("\"name\":\"getWeather\"");
    assertThat(blobJson).contains("\"location\":\"Seattle\"");
    assertThat(blobJson).contains("\"id\":\"call_456\"");
    assertThat(blobJson).contains("\"name\":\"getTime\"");
  }

  @Test
  void deserializesToolCallsWhenLoading_fromBlobPayload() {
    final var previousContext =
        AwsAgentCoreConversationContext.builder()
            .conversationId(SESSION_ID)
            .memoryId(MEMORY_ID)
            .actorId(ACTOR_ID)
            .sessionId(SESSION_ID)
            .branchName("prev-branch")
            .lastEventId("evt-2")
            .build();

    final var agentContext = AgentContext.builder().conversation(previousContext).build();

    // New format: blob envelope with blobType discriminator
    final String toolCallsBlobJson =
        "{\"blobType\":\"camunda.toolCalls\",\"version\":1,\"toolCalls\":[{\"id\":\"call_123\",\"name\":\"getWeather\",\"arguments\":{\"location\":\"Seattle\"}}]}";

    final var assistantConversational =
        Conversational.builder()
            .role(Role.ASSISTANT)
            .content(Content.fromText("Let me check the weather for you."))
            .build();

    final var event =
        Event.builder()
            .eventTimestamp(Instant.now())
            .payload(
                List.of(
                    PayloadType.builder().conversational(assistantConversational).build(),
                    PayloadType.builder().blob(Document.fromString(toolCallsBlobJson)).build()))
            .build();

    mockListEventsResponse(List.of(event));

    store.executeInSession(
        executionContext,
        agentContext,
        session -> {
          session.loadIntoRuntimeMemory(agentContext, memory);
          return agentResponse(agentContext);
        });

    assertThat(memory.allMessages()).hasSize(1);
    final var assistantMessage = (AssistantMessage) memory.allMessages().get(0);
    assertThat(assistantMessage.hasToolCalls()).isTrue();
    assertThat(assistantMessage.toolCalls()).hasSize(1);
    assertThat(assistantMessage.toolCalls().get(0).id()).isEqualTo("call_123");
  }

  @Test
  void restoresToolRoleMessagesOnLoad() {
    final var agentContext = AgentContext.empty();

    final String toolCallResultsBlobJson =
        "{\"blobType\":\"camunda.toolCallResults\",\"version\":1,\"results\":[{\"id\":\"call_1\",\"name\":\"myTool\",\"content\":\"tool output\"}]}";

    final var toolConversational =
        Conversational.builder()
            .role(Role.TOOL)
            .content(
                software.amazon.awssdk.services.bedrockagentcore.model.Content.fromText(
                    "tool output"))
            .build();

    final var event =
        Event.builder()
            .eventTimestamp(Instant.now().minusSeconds(10))
            .payload(
                List.of(
                    PayloadType.builder().conversational(toolConversational).build(),
                    PayloadType.builder()
                        .blob(Document.fromString(toolCallResultsBlobJson))
                        .build()))
            .build();

    mockListEventsResponse(List.of(event));

    store.executeInSession(
        executionContext,
        agentContext,
        session -> {
          session.loadIntoRuntimeMemory(agentContext, memory);
          return agentResponse(agentContext);
        });

    assertThat(memory.allMessages()).hasSize(1);
    assertThat(memory.allMessages().get(0)).isInstanceOf(ToolCallResultMessage.class);

    final var msg = (ToolCallResultMessage) memory.allMessages().get(0);
    assertThat(msg.results()).hasSize(1);
    assertThat(msg.results().get(0)).isInstanceOf(ToolCallResult.class);
    assertThat(msg.results().get(0).content()).isEqualTo("tool output");
  }

  @Test
  void deserializesAssistantWithTextAndToolCallsInSameEvent() {
    final var previousContext =
        AwsAgentCoreConversationContext.builder()
            .conversationId(SESSION_ID)
            .memoryId(MEMORY_ID)
            .actorId(ACTOR_ID)
            .sessionId(SESSION_ID)
            .branchName("prev-branch")
            .lastEventId("evt-1")
            .build();

    final var agentContext = AgentContext.builder().conversation(previousContext).build();

    // New format: blob envelope with blobType discriminator
    final String toolCallsBlobJson =
        "{\"blobType\":\"camunda.toolCalls\",\"version\":1,\"toolCalls\":[{\"id\":\"call_123\",\"name\":\"getWeather\",\"arguments\":{\"location\":\"Seattle\"}}]}";

    final var assistantConversational =
        Conversational.builder()
            .role(Role.ASSISTANT)
            .content(Content.fromText("Let me check the weather for you."))
            .build();

    final var event =
        Event.builder()
            .eventTimestamp(Instant.now())
            .payload(
                List.of(
                    PayloadType.builder().conversational(assistantConversational).build(),
                    PayloadType.builder().blob(Document.fromString(toolCallsBlobJson)).build()))
            .build();

    mockListEventsResponse(List.of(event));

    store.executeInSession(
        executionContext,
        agentContext,
        session -> {
          session.loadIntoRuntimeMemory(agentContext, memory);
          return agentResponse(agentContext);
        });

    assertThat(memory.allMessages()).hasSize(1);
    final var assistantMessage = (AssistantMessage) memory.allMessages().get(0);

    assertThat(assistantMessage.content()).hasSize(1);
    assertThat(
            ((io.camunda.connector.agenticai.model.message.content.TextContent)
                    assistantMessage.content().get(0))
                .text())
        .isEqualTo("Let me check the weather for you.");

    assertThat(assistantMessage.hasToolCalls()).isTrue();
    assertThat(assistantMessage.toolCalls()).hasSize(1);
    assertThat(assistantMessage.toolCalls().get(0).id()).isEqualTo("call_123");
  }

  @Test
  void deserializesAssistantWithToolCallsBlobOnlyWhenTextIsEmpty() {
    final var previousContext =
        AwsAgentCoreConversationContext.builder()
            .conversationId(SESSION_ID)
            .memoryId(MEMORY_ID)
            .actorId(ACTOR_ID)
            .sessionId(SESSION_ID)
            .branchName("prev-branch")
            .lastEventId("evt-1")
            .build();

    final var agentContext = AgentContext.builder().conversation(previousContext).build();

    // New format: blob envelope with blobType discriminator
    final String toolCallsBlobJson =
        "{\"blobType\":\"camunda.toolCalls\",\"version\":1,\"toolCalls\":[{\"id\":\"call_123\",\"name\":\"getWeather\",\"arguments\":{\"location\":\"Seattle\"}}]}";

    final var event =
        Event.builder()
            .eventTimestamp(Instant.now())
            .payload(
                List.of(PayloadType.builder().blob(Document.fromString(toolCallsBlobJson)).build()))
            .build();

    mockListEventsResponse(List.of(event));

    store.executeInSession(
        executionContext,
        agentContext,
        session -> {
          session.loadIntoRuntimeMemory(agentContext, memory);
          return agentResponse(agentContext);
        });

    assertThat(memory.allMessages()).hasSize(1);
    final var assistantMessage = (AssistantMessage) memory.allMessages().get(0);

    // No assistant text was stored => empty content list
    assertThat(assistantMessage.content()).isEmpty();

    assertThat(assistantMessage.hasToolCalls()).isTrue();
    assertThat(assistantMessage.toolCalls()).hasSize(1);
    assertThat(assistantMessage.toolCalls().get(0).id()).isEqualTo("call_123");
  }

  @Test
  void propagatesExceptionOnPartialWriteFailure() {
    final var agentContext =
        AgentContext.builder()
            .conversation(
                AwsAgentCoreConversationContext.builder()
                    .conversationId(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .sessionId(SESSION_ID)
                    .build())
            .build();

    mockListEventsResponse(List.of());
    // First event succeeds, second throws
    when(bedrockClient.createEvent(any(CreateEventRequest.class)))
        .thenReturn(createEventResponse())
        .thenThrow(BedrockAgentCoreException.builder().message("Service unavailable").build());

    assertThatThrownBy(
            () ->
                store.executeInSession(
                    executionContext,
                    agentContext,
                    session -> {
                      session.loadIntoRuntimeMemory(agentContext, memory);
                      memory.addMessage(userMessage("Hello!"));
                      memory.addMessage(assistantMessage("Hi there!"));
                      return agentResponse(session.storeFromRuntimeMemory(agentContext, memory));
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to store conversation event to AgentCore Memory")
        .hasCauseInstanceOf(BedrockAgentCoreException.class);

    // Verify first event was written, second was attempted
    verify(bedrockClient, times(2)).createEvent(any(CreateEventRequest.class));
  }

  @Test
  void propagatesExceptionOnLoadFailure() {
    final var agentContext =
        AgentContext.builder()
            .conversation(
                AwsAgentCoreConversationContext.builder()
                    .conversationId(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .sessionId(SESSION_ID)
                    .branchName("prev-branch")
                    .lastEventId("evt-2")
                    .build())
            .build();

    when(bedrockClient.listEventsPaginator(any(ListEventsRequest.class)))
        .thenThrow(BedrockAgentCoreException.builder().message("Access denied").build());

    assertThatThrownBy(
            () ->
                store.executeInSession(
                    executionContext,
                    agentContext,
                    session -> {
                      session.loadIntoRuntimeMemory(agentContext, memory);
                      return agentResponse(agentContext);
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to load conversation history from AgentCore Memory")
        .hasCauseInstanceOf(BedrockAgentCoreException.class);

    // Verify no events were written
    verify(bedrockClient, never()).createEvent(any(CreateEventRequest.class));
  }

  @Test
  void multiTurnBranchingWritesCorrectBranchStructure() {
    // === Turn 1: write to main timeline ===
    final var turn1Context = AgentContext.empty();
    mockListEventsResponse(List.of());
    when(bedrockClient.createEvent(any(CreateEventRequest.class)))
        .thenReturn(
            CreateEventResponse.builder().event(Event.builder().eventId("evt-1").build()).build())
        .thenReturn(
            CreateEventResponse.builder().event(Event.builder().eventId("evt-2").build()).build());

    final var turn1Result =
        store.executeInSession(
            executionContext,
            turn1Context,
            session -> {
              session.loadIntoRuntimeMemory(turn1Context, memory);
              memory.addMessage(userMessage("Hello!"));
              memory.addMessage(assistantMessage("Hi there!"));
              return agentResponse(session.storeFromRuntimeMemory(turn1Context, memory));
            });

    final var turn1ConversationContext =
        (AwsAgentCoreConversationContext) turn1Result.context().conversation();
    assertThat(turn1ConversationContext.branchName()).isNull();
    assertThat(turn1ConversationContext.lastEventId()).isEqualTo("evt-2");

    // === Turn 2: fork a new branch from evt-2 ===
    final var turn2Memory = new DefaultRuntimeMemory();
    final var turn2Context = AgentContext.builder().conversation(turn1ConversationContext).build();

    final var turn2Events =
        List.of(
            createEvent(Role.USER, "Hello!", Instant.now().minusSeconds(60)),
            createEvent(Role.ASSISTANT, "Hi there!", Instant.now().minusSeconds(30)));
    mockListEventsResponse(turn2Events);
    when(bedrockClient.createEvent(any(CreateEventRequest.class)))
        .thenReturn(
            CreateEventResponse.builder().event(Event.builder().eventId("evt-3").build()).build())
        .thenReturn(
            CreateEventResponse.builder().event(Event.builder().eventId("evt-4").build()).build());

    final var turn2Result =
        store.executeInSession(
            executionContext,
            turn2Context,
            session -> {
              session.loadIntoRuntimeMemory(turn2Context, turn2Memory);
              turn2Memory.addMessage(userMessage("What's the weather?"));
              turn2Memory.addMessage(assistantMessage("It's sunny!"));
              return agentResponse(session.storeFromRuntimeMemory(turn2Context, turn2Memory));
            });

    // Capture all createEvent calls across both turns
    verify(bedrockClient, times(4)).createEvent(createEventRequestCaptor.capture());
    final var allRequests = createEventRequestCaptor.getAllValues();

    // Turn 1 (indices 0-1): no branch, writes to main timeline
    assertThat(allRequests.get(0).branch()).isNull();
    assertThat(allRequests.get(1).branch()).isNull();

    // Turn 2 (indices 2-3): branch forked from evt-2
    assertThat(allRequests.get(2).branch()).isNotNull();
    assertThat(allRequests.get(2).branch().rootEventId()).isEqualTo("evt-2");
    assertThat(allRequests.get(2).branch().name()).isNotBlank();
    // Both events in turn 2 use the same branch
    assertThat(allRequests.get(3).branch().name()).isEqualTo(allRequests.get(2).branch().name());

    final var turn2ConversationContext =
        (AwsAgentCoreConversationContext) turn2Result.context().conversation();
    assertThat(turn2ConversationContext.branchName()).isEqualTo(allRequests.get(2).branch().name());
    assertThat(turn2ConversationContext.lastEventId()).isEqualTo("evt-4");
  }

  private Event createEvent(Role role, String text, Instant timestamp) {
    final var conversational =
        Conversational.builder().role(role).content(Content.fromText(text)).build();
    final var payload = PayloadType.builder().conversational(conversational).build();
    return Event.builder().eventTimestamp(timestamp).payload(payload).build();
  }

  private void mockListEventsResponse(List<Event> events) {
    // Create an SdkIterable for the events
    final SdkIterable<Event> eventsIterable = () -> events.iterator();

    when(listEventsIterable.events()).thenReturn(eventsIterable);
    when(bedrockClient.listEventsPaginator(any(ListEventsRequest.class)))
        .thenReturn(listEventsIterable);
  }

  private static int eventCounter = 0;

  private CreateEventResponse createEventResponse() {
    return CreateEventResponse.builder()
        .event(Event.builder().eventId("evt-" + (++eventCounter)).build())
        .build();
  }

  private AgentResponse agentResponse(AgentContext agentContext) {
    return AgentResponse.builder().context(agentContext).toolCalls(List.of()).build();
  }
}
