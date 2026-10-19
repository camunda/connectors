/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestAgentExecutionContext.withConfiguration;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDefinitionUpdates;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistryImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A gateway handler that defines its tools itself can declare document parameters in them. The
 * document parameters of a call are resolved on the raw tool call by the definition the model saw,
 * before the handler transforms the call, so the handler receives the resolved documents in the
 * arguments it wraps.
 */
class GatewayToolDocumentParametersTest {

  private static final String UPLOAD_TOOL = "FILES_files___upload";
  private static final String LIST_TOOL = "FILES_files___list";

  private final Document report = createDocument("report", "report.txt");
  private final Document summary = createDocument("summary", "summary.txt");
  private final String reportId = DocumentHandle.idFor(report);
  private final String summaryId = DocumentHandle.idFor(summary);
  private final DocumentRegistry registry =
      DocumentRegistry.empty()
          .withDocuments(
              List.of(
                  new IngestedDocument(report, DocumentSource.toolCall("search", "call-1", null)),
                  new IngestedDocument(summary, DocumentSource.userMessage(null))));

  private FilesGatewayToolHandler filesHandler;
  private GatewayToolHandlerRegistryImpl gatewayToolHandlers;
  private AgentResponseHandler responseHandler;
  private AgentContext discoveredContext;

  @BeforeEach
  void setUp() {
    filesHandler = new FilesGatewayToolHandler();
    gatewayToolHandlers = new GatewayToolHandlerRegistryImpl(List.of(filesHandler));
    responseHandler = new AgentResponseHandlerImpl(new ObjectMapper(), gatewayToolHandlers);

    final var discoveryResult =
        ToolCallResult.builder().id("discovery-1").name(FilesGatewayToolHandler.DISCOVERY).build();
    discoveredContext =
        gatewayToolHandlers
            .handleToolDiscoveryResults(
                AgentContext.empty().withFeatures(AgentContext.newInstanceFeatures()),
                List.of(discoveryResult))
            .agentContext();
  }

  @Test
  void discoveredGatewayToolsKeepTheDocumentFormatWithTheHintAndTheProviderGetsAPlainString() {
    final var upload = definition(discoveredContext.toolDefinitions(), UPLOAD_TOOL);

    assertThat(upload.inputSchema())
        .containsEntry(
            "properties",
            Map.of(
                "document",
                Map.of(
                    "type",
                    "string",
                    "format",
                    DocumentParameterSchema.FORMAT_DOCUMENT_ID,
                    "description",
                    "The file to upload. " + DocumentParameterSchema.HINT),
                "folder",
                Map.of("type", "string")));

    final var providerUpload =
        definition(
            DocumentParameterSchema.toProviderToolDefinitions(discoveredContext.toolDefinitions()),
            UPLOAD_TOOL);
    assertThat(providerUpload.inputSchema())
        .containsEntry(
            "properties",
            Map.of(
                "document",
                Map.of(
                    "type",
                    "string",
                    "description",
                    "The file to upload. " + DocumentParameterSchema.HINT),
                "folder",
                Map.of("type", "string")));
  }

  @Test
  void theHandlerReceivesTheResolvedDocumentOfAKnownTag() {
    final var call =
        new ToolCall(
            "call-2",
            UPLOAD_TOOL,
            Map.of("document", "<doc id=\"%s\" />".formatted(reportId), "folder", "/inbox"));

    final var response = responseHandler.createResponse(conversation(call));

    assertThat(filesHandler.receivedCalls).hasSize(1);
    final var received = filesHandler.receivedCalls.getFirst();
    assertThat(received.name()).isEqualTo(UPLOAD_TOOL);
    assertThat(received.arguments())
        .containsEntry("document", registry.findById(reportId).orElseThrow().reference())
        .containsEntry("folder", "/inbox");
    assertThat(received.arguments().get("document"))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsEntry("camunda.document.type", "camunda")
        .containsKeys("storeId", "documentId", "contentHash");

    // the handler wraps the resolved arguments into the call for the gateway element
    assertThat(response.toolCalls()).hasSize(1);
    assertThat(response.toolCalls().getFirst().metadata().name()).isEqualTo("files");
    assertThat(response.toolCalls().getFirst().arguments())
        .containsEntry("arguments", received.arguments());
  }

  @Test
  void aValueThatIsNotATagIsAnsweredWithAnErrorAndTheHandlerNeverGetsTheCall() {
    final var call = new ToolCall("call-2", UPLOAD_TOOL, Map.of("document", "does-not-exist"));
    final var conversation = conversation(call);

    final var split =
        new RuntimeToolCallHandlerImpl(new RuntimeToolRegistryImpl(List.of()))
            .split(withConfiguration(conversation.configuration()), conversation, List.of(call));
    final var answers = split.answer();

    assertThat(split.answersAllCalls()).isTrue();
    assertThat(split.remainingCalls()).isEmpty();
    assertThat(answers.results())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.id()).isEqualTo("call-2");
              assertThat(result.name()).isEqualTo(UPLOAD_TOOL);
              assertThat(result.content().toString())
                  .contains("Parameter 'document' must be the <doc /> tag of a known document")
                  .contains("got 'does-not-exist'");
            });

    // the answered call does not reach the process: the handler is not given it
    final var response = responseHandler.createResponse(conversation, split.remainingCalls());

    assertThat(filesHandler.receivedCalls).isEmpty();
    assertThat(response.toolCalls()).isEmpty();
  }

  @Test
  void aGatewayToolWithoutDocumentParametersIsLeftUntouched() {
    // arguments that look like the id of a known document and like its tag
    final var call =
        new ToolCall(
            "call-2",
            LIST_TOOL,
            Map.of(
                "folder",
                reportId,
                "nested",
                Map.of("text", "<doc id=\"%s\" />".formatted(summaryId))));
    final var conversation = conversation(call);

    final var split =
        new RuntimeToolCallHandlerImpl(new RuntimeToolRegistryImpl(List.of()))
            .split(withConfiguration(conversation.configuration()), conversation, List.of(call));
    assertThat(split.answer().results()).isEmpty();
    assertThat(split.remainingCalls()).containsExactly(call);

    responseHandler.createResponse(conversation);

    assertThat(filesHandler.receivedCalls).containsExactly(call);
  }

  private static ToolDefinition definition(List<ToolDefinition> definitions, String name) {
    return definitions.stream()
        .filter(definition -> definition.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private AgentConversation conversation(ToolCall... calls) {
    final var features = AgentContext.newInstanceFeatures();
    final var config =
        new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null)
            .withToolDefinitions(discoveredContext.toolDefinitions());
    final var context =
        AgentContext.builder()
            .state(AgentState.READY)
            .features(features)
            .toolDefinitions(discoveredContext.toolDefinitions())
            .documents(registry.entries())
            .build();
    return AgentConversation.rehydrate(
            config,
            context,
            TurnReconstructor.reconstruct(List.of()),
            null,
            List.of(userMessage("hi")))
        .ingest(assistantMessage("calling", List.of(calls)), AgentMetrics.empty());
  }

  private static Document createDocument(String content, String fileName) {
    return new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
        .create(
            DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName(fileName)
                .build());
  }

  /**
   * A gateway handler that defines its tools inline: discovery answers with an upload tool that has
   * a document parameter and a list tool that has none. It wraps the call of a tool into a call of
   * the gateway element and records the calls it is given.
   */
  private static final class FilesGatewayToolHandler implements GatewayToolHandler {

    static final String DISCOVERY = "FILES_discovery";

    final List<ToolCall> receivedCalls = new ArrayList<>();

    @Override
    public String type() {
      return "files";
    }

    @Override
    public boolean isGatewayManaged(String toolName) {
      return toolName.startsWith("FILES_");
    }

    @Override
    public String resolveElementId(String toolName) {
      return "files";
    }

    @Override
    public GatewayToolDiscoveryInitiationResult initiateToolDiscovery(
        AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions) {
      return new GatewayToolDiscoveryInitiationResult(agentContext, List.of());
    }

    @Override
    public GatewayToolDefinitionUpdates resolveUpdatedGatewayToolDefinitions(
        AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions) {
      return GatewayToolDefinitionUpdates.empty();
    }

    @Override
    public boolean allToolDiscoveryResultsPresent(
        AgentContext agentContext, List<ToolCallResult> toolCallResults) {
      return true;
    }

    @Override
    public boolean handlesToolDiscoveryResult(ToolCallResult toolCallResult) {
      return DISCOVERY.equals(toolCallResult.name());
    }

    @Override
    public List<ToolDefinition> handleToolDiscoveryResults(
        AgentContext agentContext, List<ToolCallResult> toolCallResults) {
      final var upload = new LinkedHashMap<String, Object>();
      upload.put("type", "object");
      upload.put(
          "properties",
          Map.of(
              "document",
              DocumentParameterSchema.documentSchema("The file to upload."),
              "folder",
              Map.of("type", "string")));
      return List.of(
          ToolDefinition.builder()
              .name(UPLOAD_TOOL)
              .description("Uploads a file")
              .inputSchema(upload)
              .build(),
          ToolDefinition.builder()
              .name(LIST_TOOL)
              .description("Lists a folder")
              .inputSchema(
                  Map.of(
                      "type", "object", "properties", Map.of("folder", Map.of("type", "string"))))
              .build());
    }

    @Override
    public List<ToolCall> transformToolCalls(AgentContext agentContext, List<ToolCall> toolCalls) {
      return toolCalls.stream()
          .map(
              toolCall -> {
                if (!isGatewayManaged(toolCall.name())) {
                  return toolCall;
                }
                receivedCalls.add(toolCall);
                return new ToolCall(
                    toolCall.id(),
                    "files",
                    Map.of("tool", toolCall.name(), "arguments", toolCall.arguments()));
              })
          .toList();
    }

    @Override
    public List<ToolCallResult> transformToolCallResults(
        AgentContext agentContext, List<ToolCallResult> toolCallResults) {
      return toolCallResults;
    }
  }
}
