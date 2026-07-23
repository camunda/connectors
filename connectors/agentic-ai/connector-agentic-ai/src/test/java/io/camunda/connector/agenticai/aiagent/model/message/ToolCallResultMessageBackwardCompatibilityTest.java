/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.BlobEnvelope;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

/**
 * Backward-compatibility golden-fixture tests: real Camunda-8.9-persisted {@code agentContext}
 * conversations (staged, untouched, at {@code
 * src/test/resources/backwardcompatibility/camunda-8.9/}) must still deserialize after C4
 * introduces {@link io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent} as the
 * persisted tool-call-result element type — old flat {@code ToolCallResult}-shaped JSON must lift
 * losslessly into the new structured {@code List<Content>} shape.
 */
class ToolCallResultMessageBackwardCompatibilityTest {

  private static final String FIXTURE_BASE_PATH = "/backwardcompatibility/camunda-8.9/";
  private static final String AWS_AGENTCORE_FIXTURE_BASE_PATH =
      "/backwardcompatibility/aws-agentcore/";

  // the connectors ObjectMapper, incl. the document module — see AgentContextTest for the
  // equivalent setup pattern (that test uses a plain ObjectMapper since it has no document
  // content; these fixtures do reference documents via the pointer conversation variant)
  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;

  @Test
  void inProcessFixtureDeserializesToolCallResultsAsStructuredContent() throws IOException {
    AgentContext agentContext = readFixture("agentContext-inprocess.json", AgentContext.class);

    assertThat(agentContext.conversation()).isInstanceOf(InProcessConversationContext.class);
    var conversation = (InProcessConversationContext) agentContext.conversation();

    var toolCallResultMessages = toolCallResultMessages(conversation.messages());
    assertThat(toolCallResultMessages).hasSize(4);

    assertListUsersAndJokesApiMessage(
        toolCallResultMessages.get(0), "Who is Santa's favourite singer?\n\nElf-is Presley!");
    assertLoadUserByIdMessage(toolCallResultMessages.get(1));
    assertAskHumanToSendEmailMessage(toolCallResultMessages.get(2));
    assertAskHumanToSendEmailMessage(toolCallResultMessages.get(3));

    // round-trip guard: the new format is self-consistent
    String reserialized = objectMapper.writeValueAsString(agentContext);
    AgentContext roundTripped = objectMapper.readValue(reserialized, AgentContext.class);
    assertThat(roundTripped).isEqualTo(agentContext);
  }

  @Test
  void documentPayloadFixtureDeserializesToolCallResultsAsStructuredContent() throws IOException {
    var documentContent =
        readFixture(
            "conversation-document-payload.json",
            CamundaDocumentConversationContext.DocumentContent.class);

    var toolCallResultMessages = toolCallResultMessages(documentContent.messages());
    assertThat(toolCallResultMessages).hasSize(4);

    // same structural shapes as the in-process fixture; the payload text differs (a different
    // recorded LLM run), so only the content type is pinned here, not the literal joke text
    var listUsersAndJokes = toolCallResultMessages.get(0);
    assertThat(listUsersAndJokes.results()).hasSize(2);
    assertListUsersResult(listUsersAndJokes.results().get(0));
    var jokesApi = listUsersAndJokes.results().get(1);
    assertThat(jokesApi.name()).isEqualTo("Jokes_API");
    assertThat(jokesApi.content()).singleElement().isInstanceOf(TextContent.class);

    assertLoadUserByIdMessage(toolCallResultMessages.get(1));
    assertAskHumanToSendEmailMessage(toolCallResultMessages.get(2));
    assertAskHumanToSendEmailMessage(toolCallResultMessages.get(3));

    // round-trip guard
    String reserialized = objectMapper.writeValueAsString(documentContent);
    var roundTripped =
        objectMapper.readValue(
            reserialized, CamundaDocumentConversationContext.DocumentContent.class);
    assertThat(roundTripped).isEqualTo(documentContent);
  }

  @Test
  void camundaDocumentPointerFixtureDeserializesToCamundaDocumentConversationContext()
      throws IOException {
    AgentContext agentContext =
        readFixture("agentContext-camunda-document.json", AgentContext.class);

    assertThat(agentContext.conversation()).isInstanceOf(CamundaDocumentConversationContext.class);
  }

  /**
   * AWS AgentCore golden-fixture tests: real pre-#7211 {@code camunda.toolCallResults} {@link
   * BlobEnvelope} JSON, captured from an actual AgentCore Memory session, must still deserialize
   * through the exact path {@link
   * io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper}
   * uses on load ({@code BlobEnvelope.fromDocument(...).parseData(new
   * TypeReference<List<ToolCallResultContent>>(){}, ...)}). AgentCore Memory is append-only, so
   * old- and new-shape blob envelopes coexist in the same session forever; both shapes must keep
   * deserializing.
   */
  @Test
  void toolCallResultMessage_awsAgentCoreBlobEnvelope_8_9_oldShapeLiftsToStructuredContent()
      throws IOException {
    List<JsonNode> envelopes =
        readAwsAgentCoreEnvelopeArrayFixture("toolcallresults-blob-envelopes.json");
    assertThat(envelopes).hasSize(4);

    // envelope 0: AskHumanToSendEmail, content = Map {emailSent: true}
    var askHumanToSendEmail1 = parseAwsAgentCoreToolCallResults(envelopes.get(0));
    assertThat(askHumanToSendEmail1).hasSize(1);
    var askHumanResult1 = askHumanToSendEmail1.getFirst();
    assertAwsAgentCoreResultMetadata(
        askHumanResult1, "tooluse_eB2dGtO9m9Te1MB1zPi2xs", "AskHumanToSendEmail");
    assertThat(askHumanResult1.content())
        .singleElement()
        .isInstanceOfSatisfying(
            ObjectContent.class,
            objectContent -> assertThat(objectContent.content()).isInstanceOf(Map.class));

    // envelope 1: AskHumanToSendEmail, content = Map {operatorFeedback, emailOk}
    var askHumanToSendEmail2 = parseAwsAgentCoreToolCallResults(envelopes.get(1));
    assertThat(askHumanToSendEmail2).hasSize(1);
    var askHumanResult2 = askHumanToSendEmail2.getFirst();
    assertAwsAgentCoreResultMetadata(
        askHumanResult2, "tooluse_3zcGCrRa1IgtD8jngwrNi6", "AskHumanToSendEmail");
    assertThat(askHumanResult2.content())
        .singleElement()
        .isInstanceOfSatisfying(
            ObjectContent.class,
            objectContent -> assertThat(objectContent.content()).isInstanceOf(Map.class));

    // envelope 2: LoadUserByID, content = Map
    var loadUserByID = parseAwsAgentCoreToolCallResults(envelopes.get(2));
    assertThat(loadUserByID).hasSize(1);
    var loadUserByIdResult = loadUserByID.getFirst();
    assertAwsAgentCoreResultMetadata(
        loadUserByIdResult, "tooluse_H02aOM5gXPDhUGsBp9OMjy", "LoadUserByID");
    assertThat(loadUserByIdResult.content())
        .singleElement()
        .isInstanceOfSatisfying(
            ObjectContent.class,
            objectContent -> assertThat(objectContent.content()).isInstanceOf(Map.class));

    // envelope 3: ListUsers (content = List of 10 maps) + Jokes_API (content = String)
    var listUsersAndJokes = parseAwsAgentCoreToolCallResults(envelopes.get(3));
    assertThat(listUsersAndJokes).hasSize(2);

    var listUsersResult = listUsersAndJokes.get(0);
    assertAwsAgentCoreResultMetadata(
        listUsersResult, "tooluse_FOcMCCJ59OWJOJLmfMu2gy", "ListUsers");
    assertThat(listUsersResult.content())
        .singleElement()
        .isInstanceOfSatisfying(
            ObjectContent.class,
            objectContent -> {
              assertThat(objectContent.content()).isInstanceOf(List.class);
              assertThat((List<?>) objectContent.content()).hasSize(10);
            });

    var jokesApiResult = listUsersAndJokes.get(1);
    assertAwsAgentCoreResultMetadata(jokesApiResult, "tooluse_oPt3Jcwpl536eciGfi8jC3", "Jokes_API");
    assertThat(jokesApiResult.content())
        .containsExactly(
            TextContent.textContent(
                "I just got fired from my job at the keyboard factory.\n\n"
                    + "They told me I wasn't putting in enough shifts."));
  }

  @Test
  void toolCallResultMessage_awsAgentCoreBlobEnvelope_newShapeRoundTrips() throws IOException {
    List<ToolCallResultContent> original =
        List.of(
            ToolCallResultContent.builder()
                .id("call-1")
                .name("search")
                .content(List.of(TextContent.textContent("Found 3 items")))
                .completedAt(OffsetDateTime.parse("2026-07-08T20:54:10.557+02:00"))
                .build());

    BlobEnvelope envelope = BlobEnvelope.forToolCallResults(original, objectMapper);
    Document document = envelope.toDocument(objectMapper);
    BlobEnvelope parsed = BlobEnvelope.fromDocument(document, objectMapper);
    List<ToolCallResultContent> result =
        parsed.parseData(new TypeReference<List<ToolCallResultContent>>() {}, objectMapper);

    assertThat(result).isEqualTo(original);
  }

  private void assertAwsAgentCoreResultMetadata(
      ToolCallResultContent result, String expectedId, String expectedName) {
    assertThat(result.id()).isEqualTo(expectedId);
    assertThat(result.name()).isEqualTo(expectedName);
    assertThat(result.completedAt()).isNotNull();
  }

  private List<ToolCallResultContent> parseAwsAgentCoreToolCallResults(JsonNode envelopeNode)
      throws IOException {
    Document blob = Document.fromString(objectMapper.writeValueAsString(envelopeNode));
    BlobEnvelope envelope = BlobEnvelope.fromDocument(blob, objectMapper);
    return envelope.parseData(new TypeReference<List<ToolCallResultContent>>() {}, objectMapper);
  }

  private List<JsonNode> readAwsAgentCoreEnvelopeArrayFixture(String fileName) throws IOException {
    try (InputStream stream =
        getClass().getResourceAsStream(AWS_AGENTCORE_FIXTURE_BASE_PATH + fileName)) {
      assertThat(stream).as("fixture resource %s", fileName).isNotNull();
      JsonNode root = objectMapper.readTree(stream);
      List<JsonNode> envelopes = new ArrayList<>();
      root.elements().forEachRemaining(envelopes::add);
      return envelopes;
    }
  }

  private void assertListUsersAndJokesApiMessage(
      ToolCallResultMessage message, String expectedJokeText) {
    assertThat(message.results()).hasSize(2);
    assertListUsersResult(message.results().get(0));

    var jokesApi = message.results().get(1);
    assertThat(jokesApi.name()).isEqualTo("Jokes_API");
    assertThat(jokesApi.elementId()).isNull();
    assertThat(jokesApi.completedAt()).isNull();
    assertThat(jokesApi.content()).containsExactly(TextContent.textContent(expectedJokeText));
  }

  private void assertListUsersResult(ToolCallResultContent listUsers) {
    assertThat(listUsers.id()).isNotBlank();
    assertThat(listUsers.name()).isEqualTo("ListUsers");
    assertThat(listUsers.elementId()).isNull();
    assertThat(listUsers.completedAt()).isNull();
    assertThat(listUsers.content())
        .singleElement()
        .isInstanceOfSatisfying(
            ObjectContent.class,
            objectContent -> {
              assertThat(objectContent.content()).isInstanceOf(List.class);
              assertThat((List<?>) objectContent.content()).hasSize(10);
            });
  }

  private void assertLoadUserByIdMessage(ToolCallResultMessage message) {
    assertThat(message.results()).hasSize(1);
    var loadUserByID = message.results().getFirst();
    assertThat(loadUserByID.id()).isNotBlank();
    assertThat(loadUserByID.name()).isEqualTo("LoadUserByID");
    assertThat(loadUserByID.elementId()).isNull();
    assertThat(loadUserByID.completedAt()).isNull();
    assertThat(loadUserByID.content())
        .singleElement()
        .isInstanceOfSatisfying(
            ObjectContent.class,
            objectContent -> assertThat(objectContent.content()).isInstanceOf(Map.class));
  }

  private void assertAskHumanToSendEmailMessage(ToolCallResultMessage message) {
    assertThat(message.results()).hasSize(1);
    var askHumanToSendEmail = message.results().getFirst();
    assertThat(askHumanToSendEmail.id()).isNotBlank();
    assertThat(askHumanToSendEmail.name()).isEqualTo("AskHumanToSendEmail");
    assertThat(askHumanToSendEmail.elementId()).isNull();
    assertThat(askHumanToSendEmail.completedAt()).isNull();
    assertThat(askHumanToSendEmail.content())
        .singleElement()
        .isInstanceOfSatisfying(
            ObjectContent.class,
            objectContent -> assertThat(objectContent.content()).isInstanceOf(Map.class));
  }

  private List<ToolCallResultMessage> toolCallResultMessages(List<Message> messages) {
    return messages.stream()
        .filter(ToolCallResultMessage.class::isInstance)
        .map(ToolCallResultMessage.class::cast)
        .toList();
  }

  private <T> T readFixture(String fileName, Class<T> type) throws IOException {
    try (InputStream stream = getClass().getResourceAsStream(FIXTURE_BASE_PATH + fileName)) {
      assertThat(stream).as("fixture resource %s", fileName).isNotNull();
      return objectMapper.readValue(stream, type);
    }
  }
}
