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

import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStore.BedrockAgentCoreClientFactory;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.AwsAgentCoreMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.InProcessMemoryStorageConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.util.TestObjectMapperSupplier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
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
import software.amazon.awssdk.services.bedrockagentcore.model.MetadataValue;
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
  private AwsAgentCoreMemoryStorageConfiguration config;

  @BeforeEach
  void setUp() {
    var authentication =
        new AwsAgentCoreMemoryStorageConfiguration.AwsAgentCoreAuthentication
            .AwsStaticCredentialsAuthentication("test-access-key", "test-secret-key");
    config =
        new AwsAgentCoreMemoryStorageConfiguration(
            "us-east-1", null, authentication, MEMORY_ID, ACTOR_ID);
    lenient().when(executionContext.memory()).thenReturn(new MemoryConfiguration(config, 20));
    lenient().when(clientFactory.createClient(config)).thenReturn(bedrockClient);

    var conversationMapper = new AwsAgentCoreConversationMapper(TestObjectMapperSupplier.INSTANCE);

    store = new AwsAgentCoreConversationStore(clientFactory, conversationMapper);
  }

  @Test
  void storeTypeIsAlignedWithConfiguration() {
    assertThat(store.type()).isEqualTo(config.storeType()).isEqualTo("aws-agentcore");
  }

  @Test
  void throwsExceptionForMissingConfiguration() {
    final var agentContext = AgentContext.empty();
    when(executionContext.memory()).thenReturn(new MemoryConfiguration(null, 20));

    assertThatThrownBy(() -> store.createSession(executionContext, agentContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Expected memory storage configuration to be of type AwsAgentCoreMemoryStorageConfiguration, but got: null");
  }

  @Test
  void throwsExceptionForUnsupportedConfiguration() {
    final var agentContext = AgentContext.empty();
    when(executionContext.memory())
        .thenReturn(new MemoryConfiguration(new InProcessMemoryStorageConfiguration(), 20));

    assertThatThrownBy(() -> store.createSession(executionContext, agentContext))
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
                AwsAgentCoreConversationContext.builder(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
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

    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      // Verify messages were loaded
      assertThat(loadResult.messages()).hasSize(2);
    }

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
                AwsAgentCoreConversationContext.builder(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .build())
            .build();

    // Mock empty list events response for new conversation
    mockListEventsResponse(List.of());
    when(bedrockClient.createEvent(any(CreateEventRequest.class)))
        .thenReturn(createEventResponse());

    AgentContext updatedAgentContext;
    try (var session = store.createSession(executionContext, agentContext)) {
      session.loadMessages(agentContext);

      List<Message> messages = List.of(userMessage("Hello!"), assistantMessage("Hi there!"));
      var updatedConversation =
          session.storeMessages(agentContext, ConversationStoreRequest.of(messages));
      updatedAgentContext = agentContext.withConversation(updatedConversation);
    }

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
    final var conversation = updatedAgentContext.conversation();
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
                AwsAgentCoreConversationContext.builder(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
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

    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      // Build messages list: loaded + one new
      var allMessages = new ArrayList<>(loadResult.messages());
      allMessages.add(userMessage("What's the weather?"));

      session.storeMessages(agentContext, ConversationStoreRequest.of(allMessages));
    }

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

    AgentContext updatedAgentContext;
    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);
      assertThat(loadResult.messages()).isEmpty();

      var updatedConversation =
          session.storeMessages(agentContext, ConversationStoreRequest.of(List.of()));
      updatedAgentContext = agentContext.withConversation(updatedConversation);
    }

    // Verify conversation context was created
    assertThat(updatedAgentContext.conversation())
        .isInstanceOf(AwsAgentCoreConversationContext.class);
  }

  @Test
  void usesStaticCredentialsAuthentication() {
    // Verify that the client factory is called with the correct configuration including auth
    final var agentContext = AgentContext.empty();
    mockListEventsResponse(List.of());

    try (var session = store.createSession(executionContext, agentContext)) {
      session.loadMessages(agentContext);
    }

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
                AwsAgentCoreConversationContext.builder(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .build())
            .build();

    // Mock empty list events response for new conversation
    mockListEventsResponse(List.of());
    when(bedrockClient.createEvent(any(CreateEventRequest.class)))
        .thenReturn(createEventResponse());

    AwsAgentCoreConversationContext agentCoreContext;
    try (var session = store.createSession(executionContext, agentContext)) {
      session.loadMessages(agentContext);

      // Add system message (should be skipped from AgentCore), user message, and assistant message
      List<Message> messages =
          List.of(
              systemMessage("You are a helpful assistant."),
              userMessage("Hello!"),
              assistantMessage("Hi there!"));
      var updatedConversation =
          session.storeMessages(agentContext, ConversationStoreRequest.of(messages));
      agentCoreContext = (AwsAgentCoreConversationContext) updatedConversation;
    }

    // Verify only 2 messages were stored to AgentCore (user and assistant), system message was
    // skipped
    verify(bedrockClient, times(2)).createEvent(createEventRequestCaptor.capture());
    final var requests = createEventRequestCaptor.getAllValues();
    assertThat(requests).hasSize(2);
    assertThat(requests.get(0).clientToken()).endsWith(":0");
    assertThat(requests.get(1).clientToken()).endsWith(":1");

    // Verify conversation context was updated with branch info
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
        AwsAgentCoreConversationContext.builder(SESSION_ID)
            .memoryId(MEMORY_ID)
            .actorId(ACTOR_ID)
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

    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      // Verify 3 messages: 1 system (restored) + 2 from AgentCore
      assertThat(loadResult.messages()).hasSize(3);
      assertThat(loadResult.messages().get(0)).isInstanceOf(SystemMessage.class);
      assertThat(loadResult.messages().get(1)).isInstanceOf(UserMessage.class);
      assertThat(loadResult.messages().get(2)).isInstanceOf(AssistantMessage.class);
    }
  }

  @Test
  void storesAssistantMessageWithToolCalls() throws JSONException {
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

    try (var session = store.createSession(executionContext, agentContext)) {
      session.loadMessages(agentContext);

      List<Message> messages =
          List.of(
              userMessage("What's the weather and time in Seattle?"),
              assistantMessage(
                  "Let me check the weather and time for you.", List.of(toolCall1, toolCall2)));
      session.storeMessages(agentContext, ConversationStoreRequest.of(messages));
    }

    // Verify 2 messages were stored
    verify(bedrockClient, times(2)).createEvent(createEventRequestCaptor.capture());
    final var requests = createEventRequestCaptor.getAllValues();
    assertThat(requests).hasSize(2);
    assertThat(requests.get(0).clientToken()).endsWith(":0");
    assertThat(requests.get(1).clientToken()).endsWith(":1");

    // Verify assistant event stores:
    // - conversational payload with plain assistant text
    // - blob payload containing tool call JSON envelope
    // - metadata blob with role
    final var assistantEventPayloads = requests.get(1).payload();
    assertThat(assistantEventPayloads).hasSize(3);

    final var assistantConversational = assistantEventPayloads.get(0).conversational();
    assertThat(assistantConversational).isNotNull();
    assertThat(assistantConversational.role()).isEqualTo(Role.ASSISTANT);
    assertThat(assistantConversational.content().text())
        .isEqualTo("Let me check the weather and time for you.");

    final var blob = assistantEventPayloads.get(1).blob();
    assertThat(blob).isNotNull();
    JSONAssert.assertEquals(
        """
        {
          "blobType": "camunda.toolCalls",
          "version": 1,
          "toolCalls": [
            { "id": "call_123", "name": "getWeather", "arguments": { "location": "Seattle" } },
            { "id": "call_456", "name": "getTime", "arguments": {} }
          ]
        }
        """,
        blob.asString(),
        true);
  }

  @Test
  void deserializesToolCallsWhenLoading_fromBlobPayload() {
    final var previousContext =
        AwsAgentCoreConversationContext.builder(SESSION_ID)
            .memoryId(MEMORY_ID)
            .actorId(ACTOR_ID)
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
                    PayloadType.builder().blob(Document.fromString(toolCallsBlobJson)).build(),
                    metadataBlobPayload(Role.ASSISTANT)))
            .build();

    mockListEventsResponse(List.of(event));

    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      assertThat(loadResult.messages()).hasSize(1);
      final var assistantMessage = (AssistantMessage) loadResult.messages().get(0);
      assertThat(assistantMessage.hasToolCalls()).isTrue();
      assertThat(assistantMessage.toolCalls()).hasSize(1);
      assertThat(assistantMessage.toolCalls().get(0).id()).isEqualTo("call_123");
    }
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
                        .build(),
                    metadataBlobPayload(Role.TOOL)))
            .build();

    mockListEventsResponse(List.of(event));

    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      assertThat(loadResult.messages()).hasSize(1);
      assertThat(loadResult.messages().get(0)).isInstanceOf(ToolCallResultMessage.class);

      final var msg = (ToolCallResultMessage) loadResult.messages().get(0);
      assertThat(msg.results()).hasSize(1);
      assertThat(msg.results().get(0)).isInstanceOf(ToolCallResult.class);
      assertThat(msg.results().get(0).content()).isEqualTo("tool output");
    }
  }

  @Test
  void deserializesAssistantWithTextAndToolCallsInSameEvent() {
    final var previousContext =
        AwsAgentCoreConversationContext.builder(SESSION_ID)
            .memoryId(MEMORY_ID)
            .actorId(ACTOR_ID)
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
                    PayloadType.builder().blob(Document.fromString(toolCallsBlobJson)).build(),
                    metadataBlobPayload(Role.ASSISTANT)))
            .build();

    mockListEventsResponse(List.of(event));

    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      assertThat(loadResult.messages()).hasSize(1);
      final var assistantMessage = (AssistantMessage) loadResult.messages().get(0);

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
  }

  @Test
  void deserializesAssistantWithToolCallsBlobOnlyWhenTextIsEmpty() {
    final var previousContext =
        AwsAgentCoreConversationContext.builder(SESSION_ID)
            .memoryId(MEMORY_ID)
            .actorId(ACTOR_ID)
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
                List.of(
                    PayloadType.builder().blob(Document.fromString(toolCallsBlobJson)).build(),
                    metadataBlobPayload(Role.ASSISTANT)))
            .build();

    mockListEventsResponse(List.of(event));

    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      assertThat(loadResult.messages()).hasSize(1);
      final var assistantMessage = (AssistantMessage) loadResult.messages().get(0);

      // No assistant text was stored => empty content list
      assertThat(assistantMessage.content()).isEmpty();

      assertThat(assistantMessage.hasToolCalls()).isTrue();
      assertThat(assistantMessage.toolCalls()).hasSize(1);
      assertThat(assistantMessage.toolCalls().get(0).id()).isEqualTo("call_123");
    }
  }

  @Test
  void propagatesExceptionOnPartialWriteFailure() {
    final var agentContext =
        AgentContext.builder()
            .conversation(
                AwsAgentCoreConversationContext.builder(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .build())
            .build();

    mockListEventsResponse(List.of());
    // First event succeeds, second throws
    when(bedrockClient.createEvent(any(CreateEventRequest.class)))
        .thenReturn(createEventResponse())
        .thenThrow(BedrockAgentCoreException.builder().message("Service unavailable").build());

    assertThatThrownBy(
            () -> {
              try (var session = store.createSession(executionContext, agentContext)) {
                session.loadMessages(agentContext);

                List<Message> messages =
                    List.of(userMessage("Hello!"), assistantMessage("Hi there!"));
                session.storeMessages(agentContext, ConversationStoreRequest.of(messages));
              }
            })
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
                AwsAgentCoreConversationContext.builder(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .branchName("prev-branch")
                    .lastEventId("evt-2")
                    .build())
            .build();

    when(bedrockClient.listEventsPaginator(any(ListEventsRequest.class)))
        .thenThrow(BedrockAgentCoreException.builder().message("Access denied").build());

    assertThatThrownBy(
            () -> {
              try (var session = store.createSession(executionContext, agentContext)) {
                session.loadMessages(agentContext);
              }
            })
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

    AwsAgentCoreConversationContext turn1ConversationContext;
    try (var session = store.createSession(executionContext, turn1Context)) {
      session.loadMessages(turn1Context);

      List<Message> messages = List.of(userMessage("Hello!"), assistantMessage("Hi there!"));
      var updatedConversation =
          session.storeMessages(turn1Context, ConversationStoreRequest.of(messages));
      turn1ConversationContext = (AwsAgentCoreConversationContext) updatedConversation;
    }

    assertThat(turn1ConversationContext.branchName()).isNull();
    assertThat(turn1ConversationContext.lastEventId()).isEqualTo("evt-2");

    // === Turn 2: fork a new branch from evt-2 ===
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

    AwsAgentCoreConversationContext turn2ConversationContext;
    try (var session = store.createSession(executionContext, turn2Context)) {
      var loadResult = session.loadMessages(turn2Context);

      var allMessages = new ArrayList<>(loadResult.messages());
      allMessages.add(userMessage("What's the weather?"));
      allMessages.add(assistantMessage("It's sunny!"));

      var updatedConversation =
          session.storeMessages(turn2Context, ConversationStoreRequest.of(allMessages));
      turn2ConversationContext = (AwsAgentCoreConversationContext) updatedConversation;
    }

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

    assertThat(turn2ConversationContext.branchName()).isEqualTo(allRequests.get(2).branch().name());
    assertThat(turn2ConversationContext.lastEventId()).isEqualTo("evt-4");
  }

  @Test
  void throwsExceptionWhenMemoryIdChangedBetweenIterations() {
    // Turn 1: establish context with original memoryId
    final var turn1Context =
        AgentContext.builder()
            .conversation(
                AwsAgentCoreConversationContext.builder()
                    .conversationId(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .build())
            .build();

    // Change memoryId in config for turn 2
    var changedConfig =
        new AwsAgentCoreMemoryStorageConfiguration(
            "us-east-1", null, config.authentication(), "different-memory-id", ACTOR_ID);
    when(executionContext.memory()).thenReturn(new MemoryConfiguration(changedConfig, 20));
    when(clientFactory.createClient(changedConfig)).thenReturn(bedrockClient);

    var changedStore =
        new AwsAgentCoreConversationStore(
            clientFactory, new AwsAgentCoreConversationMapper(TestObjectMapperSupplier.INSTANCE));

    assertThatThrownBy(
            () -> {
              try (var session = changedStore.createSession(executionContext, turn1Context)) {
                session.loadMessages(turn1Context);
              }
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("memoryId changed between iterations");
  }

  @Test
  void throwsExceptionWhenActorIdChangedBetweenIterations() {
    // Turn 1: establish context with original actorId
    final var turn1Context =
        AgentContext.builder()
            .conversation(
                AwsAgentCoreConversationContext.builder()
                    .conversationId(SESSION_ID)
                    .memoryId(MEMORY_ID)
                    .actorId(ACTOR_ID)
                    .build())
            .build();

    // Change actorId in config for turn 2
    var changedConfig =
        new AwsAgentCoreMemoryStorageConfiguration(
            "us-east-1", null, config.authentication(), MEMORY_ID, "different-actor-id");
    when(executionContext.memory()).thenReturn(new MemoryConfiguration(changedConfig, 20));
    when(clientFactory.createClient(changedConfig)).thenReturn(bedrockClient);

    var changedStore =
        new AwsAgentCoreConversationStore(
            clientFactory, new AwsAgentCoreConversationMapper(TestObjectMapperSupplier.INSTANCE));

    assertThatThrownBy(
            () -> {
              try (var session = changedStore.createSession(executionContext, turn1Context)) {
                session.loadMessages(turn1Context);
              }
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("actorId changed between iterations");
  }

  @Test
  void ordersEventsByTimestampThenBySeqMetadata() {
    // given - events with identical timestamps but different seq values, in reverse order
    final var timestamp = Instant.now();
    final var events =
        List.of(
            createEventWithSeq(Role.ASSISTANT, "Second", timestamp, "1"),
            createEventWithSeq(Role.USER, "First", timestamp, "0"));

    final var agentContext = AgentContext.empty();
    mockListEventsResponse(events);

    // when
    try (var session = store.createSession(executionContext, agentContext)) {
      var loadResult = session.loadMessages(agentContext);

      // then - messages should be ordered by seq: User (0) before Assistant (1)
      assertThat(loadResult.messages()).hasSize(2);
      assertThat(loadResult.messages().get(0)).isInstanceOf(UserMessage.class);
      assertThat(loadResult.messages().get(1)).isInstanceOf(AssistantMessage.class);
    }
  }

  @Test
  void closesClientWhenSessionIsClosed() {
    final var agentContext = AgentContext.empty();
    mockListEventsResponse(List.of());

    var session = store.createSession(executionContext, agentContext);
    session.loadMessages(agentContext);
    session.close();

    verify(bedrockClient).close();
  }

  private Event createEventWithSeq(Role role, String text, Instant timestamp, String seq) {
    final var conversational =
        Conversational.builder().role(role).content(Content.fromText(text)).build();
    return Event.builder()
        .eventTimestamp(timestamp)
        .payload(
            PayloadType.builder().conversational(conversational).build(), metadataBlobPayload(role))
        .metadata(Map.of("seq", MetadataValue.fromStringValue(seq)))
        .build();
  }

  private Event createEvent(Role role, String text, Instant timestamp) {
    final var conversational =
        Conversational.builder().role(role).content(Content.fromText(text)).build();
    return Event.builder()
        .eventTimestamp(timestamp)
        .payload(
            PayloadType.builder().conversational(conversational).build(), metadataBlobPayload(role))
        .build();
  }

  private static PayloadType metadataBlobPayload(Role role) {
    String json =
        "{\"blobType\":\"camunda.messageMetadata\",\"version\":1,\"metadata\":{},\"properties\":{\"role\":\""
            + role.toString()
            + "\"}}";
    return PayloadType.builder().blob(Document.fromString(json)).build();
  }

  private void mockListEventsResponse(List<Event> events) {
    // Create an SdkIterable for the events
    final SdkIterable<Event> eventsIterable = () -> events.iterator();

    when(listEventsIterable.events()).thenReturn(eventsIterable);
    when(bedrockClient.listEventsPaginator(any(ListEventsRequest.class)))
        .thenReturn(listEventsIterable);
  }

  private int eventCounter = 0;

  private CreateEventResponse createEventResponse() {
    return CreateEventResponse.builder()
        .event(Event.builder().eventId("evt-" + (++eventCounter)).build())
        .build();
  }
}
