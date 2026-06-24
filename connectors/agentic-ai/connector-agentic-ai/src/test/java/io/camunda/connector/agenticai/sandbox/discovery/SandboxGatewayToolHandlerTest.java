/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.discovery;

import static io.camunda.connector.agenticai.sandbox.discovery.SandboxGatewayToolHandler.PROPERTY_SANDBOX;
import static io.camunda.connector.agenticai.sandbox.discovery.SandboxGatewayToolHandler.SANDBOX_DISCOVERY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetadata;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SandboxGatewayToolHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private SandboxGatewayToolHandler handler;

  @BeforeEach
  void setUp() {
    handler = new SandboxGatewayToolHandler(objectMapper);
  }

  // -------------------------------------------------------------------------
  // Type identification
  // -------------------------------------------------------------------------

  @Nested
  class TypeIdentification {

    @Test
    void returnsCorrectType() {
      assertThat(handler.type()).isEqualTo("sandbox");
    }

    @Test
    void resolveElementId_returnsToolNameUnchanged() {
      // Sandbox tool names do not encode element id; the tool name is returned as-is
      // so the registry's .orElse(result.name()) fallback is a no-op.
      assertThat(handler.resolveElementId("sandbox_bash")).isEqualTo("sandbox_bash");
      assertThat(handler.resolveElementId("sandbox_fs_read")).isEqualTo("sandbox_fs_read");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          SandboxToolNames.BASH,
          SandboxToolNames.FS_READ,
          SandboxToolNames.FS_WRITE,
          SandboxToolNames.EXPORT_DOCUMENT,
          SandboxToolNames.IMPORT_DOCUMENT
        })
    void isGatewayManaged_trueForSandboxTools(String toolName) {
      assertThat(handler.isGatewayManaged(toolName)).isTrue();
    }

    @Test
    void isGatewayManaged_falseForOtherTool() {
      assertThat(handler.isGatewayManaged("other_tool")).isFalse();
    }

    @Test
    void isGatewayManaged_falseForNull() {
      assertThat(handler.isGatewayManaged(null)).isFalse();
    }
  }

  // -------------------------------------------------------------------------
  // Tool discovery initiation
  // -------------------------------------------------------------------------

  @Nested
  class ToolDiscoveryInitiation {

    @Test
    void returnsEmptyResult_whenNoSandboxGatewayToolDefinitions() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("mcpClient", "mcp1"),
              createGatewayToolDefinition("a2aClient", "a2a1"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      assertThat(result.agentContext()).isEqualTo(agentContext);
      assertThat(result.toolDiscoveryToolCalls()).isEmpty();
    }

    @Test
    void createsDiscoveryToolCall_whenOneSandboxGatewayToolDefinitionPresent() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(createGatewayToolDefinition("sandbox", "Sandbox_Gateway_1"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      // initiateToolDiscovery now stores a SandboxState (elementId only, handle/workDir null)
      final var storedRaw = result.agentContext().properties().get(PROPERTY_SANDBOX);
      assertThat(storedRaw).isInstanceOf(SandboxState.class);
      final var storedState = (SandboxState) storedRaw;
      assertThat(storedState.elementId()).isEqualTo("Sandbox_Gateway_1");
      assertThat(storedState.handle()).isNull();
      assertThat(storedState.workDir()).isNull();
      assertThat(storedState.catalog()).isNullOrEmpty();

      assertThat(result.toolDiscoveryToolCalls()).hasSize(1);
      assertThat(result.toolDiscoveryToolCalls().getFirst())
          .satisfies(
              toolCall -> {
                assertThat(toolCall.id()).isEqualTo(SANDBOX_DISCOVERY_PREFIX + "Sandbox_Gateway_1");
                assertThat(toolCall.name()).isEqualTo("Sandbox_Gateway_1");
                assertThat(toolCall.arguments())
                    .containsEntry("operation", SandboxOperation.CREATE)
                    .doesNotContainKey("agentInstanceKey");
              });
    }

    @Test
    void createsDiscoveryToolCall_withAgentInstanceKey_whenMetadataPresent() {
      var metadata = new AgentMetadata(1001L, 2002L, 3003L, null);
      var agentContext = AgentContext.empty().withMetadata(metadata);
      var gatewayToolDefinitions =
          List.of(createGatewayToolDefinition("sandbox", "Sandbox_Gateway_1"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      assertThat(result.toolDiscoveryToolCalls()).hasSize(1);
      assertThat(result.toolDiscoveryToolCalls().getFirst().arguments())
          .containsEntry("operation", SandboxOperation.CREATE)
          .containsEntry("agentInstanceKey", 3003L);
    }

    @Test
    void createsDiscoveryToolCall_withoutAgentInstanceKey_whenMetadataHasNullKey() {
      var metadata = new AgentMetadata(1001L, 2002L, null, null);
      var agentContext = AgentContext.empty().withMetadata(metadata);
      var gatewayToolDefinitions =
          List.of(createGatewayToolDefinition("sandbox", "Sandbox_Gateway_1"));

      var result = handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions);

      assertThat(result.toolDiscoveryToolCalls()).hasSize(1);
      assertThat(result.toolDiscoveryToolCalls().getFirst().arguments())
          .containsEntry("operation", SandboxOperation.CREATE)
          .doesNotContainKey("agentInstanceKey");
    }

    @Test
    void throwsException_whenMultipleSandboxGatewayToolDefinitionsPresent() {
      var agentContext = AgentContext.empty();
      var gatewayToolDefinitions =
          List.of(
              createGatewayToolDefinition("sandbox", "Sandbox_1"),
              createGatewayToolDefinition("sandbox", "Sandbox_2"));

      assertThatThrownBy(() -> handler.initiateToolDiscovery(agentContext, gatewayToolDefinitions))
          .isInstanceOf(ConnectorException.class)
          .asInstanceOf(InstanceOfAssertFactories.type(ConnectorException.class))
          .satisfies(
              e -> {
                assertThat(e.getErrorCode()).isEqualTo("SANDBOX_MULTIPLE_NOT_ALLOWED");
                assertThat(e.getMessage())
                    .contains("Only one sandbox gateway element is allowed per process")
                    .contains("'Sandbox_1'")
                    .contains("'Sandbox_2'");
              });
    }
  }

  // -------------------------------------------------------------------------
  // All tool discovery results present
  // -------------------------------------------------------------------------

  @Nested
  class AllToolDiscoveryResultsPresent {

    @Test
    void returnsTrue_whenNoSandboxTracked() {
      var agentContext = AgentContext.empty();

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, List.of())).isTrue();
    }

    @Test
    void returnsTrue_whenSandboxTrackedAndResultPresent() {
      // properties["sandbox"] is now a SandboxState
      var sandboxState = SandboxState.builder().elementId("Sandbox_Gateway_1").build();
      var agentContext = AgentContext.empty().withProperty(PROPERTY_SANDBOX, sandboxState);
      var toolCallResults =
          List.of(
              ToolCallResult.builder()
                  .id(SANDBOX_DISCOVERY_PREFIX + "Sandbox_Gateway_1")
                  .name("Sandbox_Gateway_1")
                  .content(Map.of("handle", "h1", "workDir", "/workspace"))
                  .build());

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, toolCallResults)).isTrue();
    }

    @Test
    void returnsFalse_whenSandboxTrackedButResultAbsent() {
      var sandboxState = SandboxState.builder().elementId("Sandbox_Gateway_1").build();
      var agentContext = AgentContext.empty().withProperty(PROPERTY_SANDBOX, sandboxState);

      assertThat(handler.allToolDiscoveryResultsPresent(agentContext, List.of())).isFalse();
    }
  }

  // -------------------------------------------------------------------------
  // Handles tool discovery result
  // -------------------------------------------------------------------------

  @Nested
  class HandlesToolDiscoveryResult {

    @Test
    void returnsTrue_whenIdStartsWithSandboxDiscoveryPrefix() {
      var result =
          ToolCallResult.builder()
              .id(SANDBOX_DISCOVERY_PREFIX + "MyElem")
              .name("MyElem")
              .content("content")
              .build();

      assertThat(handler.handlesToolDiscoveryResult(result)).isTrue();
    }

    @Test
    void returnsFalse_whenIdDoesNotStartWithPrefix() {
      var result =
          ToolCallResult.builder().id("other_result").name("other").content("content").build();

      assertThat(handler.handlesToolDiscoveryResult(result)).isFalse();
    }

    @Test
    void returnsFalse_whenIdIsNull() {
      var result = ToolCallResult.builder().name("MyElem").content("content").build();

      assertThat(handler.handlesToolDiscoveryResult(result)).isFalse();
    }
  }

  // -------------------------------------------------------------------------
  // Handle tool discovery results
  // -------------------------------------------------------------------------

  @Nested
  class HandleToolDiscoveryResults {

    @Test
    void convertsCreateResult_toFiveSandboxToolDefinitions() {
      var agentContext = AgentContext.empty();
      var content =
          Map.of(
              "handle",
              "handle-abc",
              "workDir",
              "/workspace",
              "catalog",
              List.of(
                  Map.of("name", "my-skill", "description", "does something", "location", "loc1")));
      var discoveryResult =
          ToolCallResult.builder()
              .id(SANDBOX_DISCOVERY_PREFIX + "MyElem")
              .name("MyElem")
              .content(content)
              .build();

      var toolDefs = handler.handleToolDiscoveryResults(agentContext, List.of(discoveryResult));

      assertThat(toolDefs).hasSize(5);
      assertThat(toolDefs)
          .allSatisfy(def -> assertThat("sandbox".equals(def.gatewayType())).isTrue());
      // Per-tool metadata must only contain gatewayType and operation — no
      // elementId/handle/workDir/catalog
      assertThat(toolDefs)
          .allSatisfy(
              def ->
                  assertThat(def.metadata())
                      .containsOnlyKeys(
                          ToolDefinition.METADATA_GATEWAY_TYPE,
                          SandboxToolDefinitions.METADATA_OPERATION));
    }

    @Test
    void convertsCreateResult_withoutCatalog() {
      var agentContext = AgentContext.empty();
      var content = Map.of("handle", "h1", "workDir", "/ws");
      var discoveryResult =
          ToolCallResult.builder()
              .id(SANDBOX_DISCOVERY_PREFIX + "MyElem")
              .name("MyElem")
              .content(content)
              .build();

      var toolDefs = handler.handleToolDiscoveryResults(agentContext, List.of(discoveryResult));

      assertThat(toolDefs).hasSize(5);
      assertThat(toolDefs)
          .allSatisfy(
              def ->
                  assertThat(def.metadata())
                      .containsOnlyKeys(
                          ToolDefinition.METADATA_GATEWAY_TYPE,
                          SandboxToolDefinitions.METADATA_OPERATION));
    }
  }

  // -------------------------------------------------------------------------
  // Contribute discovery context
  // -------------------------------------------------------------------------

  @Nested
  class ContributeDiscoveryContext {

    @Test
    void storesSandboxState_withHandleWorkDirCatalog() {
      var agentContext = AgentContext.empty();
      var catalog =
          List.of(Map.of("name", "my-skill", "description", "does something", "location", "loc1"));
      var content = Map.of("handle", "handle-abc", "workDir", "/workspace", "catalog", catalog);
      var discoveryResult =
          ToolCallResult.builder()
              .id(SANDBOX_DISCOVERY_PREFIX + "MyElem")
              .name("MyElem")
              .content(content)
              .build();

      var updatedContext =
          handler.contributeDiscoveryContext(agentContext, List.of(discoveryResult));

      final var raw = updatedContext.properties().get(PROPERTY_SANDBOX);
      assertThat(raw).isInstanceOf(SandboxState.class);
      final var state = (SandboxState) raw;
      assertThat(state.elementId()).isEqualTo("MyElem");
      assertThat(state.handle()).isEqualTo("handle-abc");
      assertThat(state.workDir()).isEqualTo("/workspace");
      assertThat(state.catalog()).isNotNull().hasSize(1);
      assertThat(state.catalog().getFirst().name()).isEqualTo("my-skill");
    }

    @Test
    void storesSandboxState_withoutCatalog() {
      var agentContext = AgentContext.empty();
      var content = Map.of("handle", "h1", "workDir", "/ws");
      var discoveryResult =
          ToolCallResult.builder()
              .id(SANDBOX_DISCOVERY_PREFIX + "MyElem")
              .name("MyElem")
              .content(content)
              .build();

      var updatedContext =
          handler.contributeDiscoveryContext(agentContext, List.of(discoveryResult));

      final var state = (SandboxState) updatedContext.properties().get(PROPERTY_SANDBOX);
      assertThat(state.elementId()).isEqualTo("MyElem");
      assertThat(state.handle()).isEqualTo("h1");
      assertThat(state.workDir()).isEqualTo("/ws");
      assertThat(state.catalog()).isNullOrEmpty();
    }

    @Test
    void returnsAgentContextUnchanged_whenNoResults() {
      var agentContext = AgentContext.empty();

      var updatedContext = handler.contributeDiscoveryContext(agentContext, List.of());

      assertThat(updatedContext).isEqualTo(agentContext);
    }
  }

  // -------------------------------------------------------------------------
  // Transform tool calls
  // -------------------------------------------------------------------------

  @Nested
  class TransformToolCalls {

    private AgentContext agentContextWithState(String elementId, String handle) {
      var sandboxState =
          SandboxState.builder().elementId(elementId).handle(handle).workDir("/ws").build();
      var toolDefs = SandboxToolDefinitions.sandboxToolDefinitions();
      return AgentContext.empty()
          .withProperty(PROPERTY_SANDBOX, sandboxState)
          .withToolDefinitions(toolDefs);
    }

    @Test
    void rewritesSandboxBashCall_toElementCallWithOperation() {
      var agentContext = agentContextWithState("Sandbox_Gateway_1", "handle-xyz");
      var toolCalls =
          List.of(new ToolCall("call-1", SandboxToolNames.BASH, Map.of("command", "ls -la")));

      var result = handler.transformToolCalls(agentContext, DocumentRegistry.empty(), toolCalls);

      assertThat(result).hasSize(1);
      assertThat(result.getFirst())
          .satisfies(
              tc -> {
                assertThat(tc.id()).isEqualTo("call-1");
                assertThat(tc.name()).isEqualTo("Sandbox_Gateway_1");
                assertThat(tc.arguments())
                    .containsEntry("operation", SandboxOperation.BASH)
                    .containsEntry("handle", "handle-xyz")
                    .containsEntry("command", "ls -la");
              });
    }

    @Test
    void passesThrough_nonSandboxToolCalls() {
      var sandboxState = SandboxState.builder().elementId("Sandbox_Gateway_1").build();
      var agentContext = AgentContext.empty().withProperty(PROPERTY_SANDBOX, sandboxState);
      var toolCalls = List.of(new ToolCall("call-2", "some_regular_tool", Map.of("arg", "value")));

      var result = handler.transformToolCalls(agentContext, DocumentRegistry.empty(), toolCalls);

      assertThat(result).isEqualTo(toolCalls);
    }

    @Test
    void passesThrough_sandboxToolCallWithoutMatchingToolDefinition() {
      // No tool definitions in context → gatewayType check fails, so pass through
      var sandboxState = SandboxState.builder().elementId("Sandbox_Gateway_1").build();
      var agentContext = AgentContext.empty().withProperty(PROPERTY_SANDBOX, sandboxState);
      var toolCalls =
          List.of(new ToolCall("call-3", SandboxToolNames.FS_READ, Map.of("path", "/file.txt")));

      var result = handler.transformToolCalls(agentContext, DocumentRegistry.empty(), toolCalls);

      assertThat(result).isEqualTo(toolCalls);
    }

    @Test
    void importDocument_resolvesIdToReference_whenPresentInRegistry() {
      var reference = new CamundaDocumentReferenceModel("store-1", "doc-id-1", null, null);
      var entry = new DocumentRegistryEntry("known-doc-id", reference, "file.txt", "text/plain");
      var registry = DocumentRegistry.of(List.of(entry));
      var agentContext = agentContextWithState("Sandbox_Gateway_1", "handle-xyz");
      var toolCalls =
          List.of(
              new ToolCall(
                  "call-import",
                  SandboxToolNames.IMPORT_DOCUMENT,
                  Map.of("id", "known-doc-id", "path", "/dest/file.txt")));

      var result = handler.transformToolCalls(agentContext, registry, toolCalls);

      assertThat(result).hasSize(1);
      assertThat(result.getFirst())
          .satisfies(
              tc -> {
                assertThat(tc.id()).isEqualTo("call-import");
                assertThat(tc.name()).isEqualTo("Sandbox_Gateway_1");
                assertThat(tc.arguments())
                    .containsEntry("operation", SandboxOperation.IMPORT_DOCUMENT)
                    .containsEntry("handle", "handle-xyz")
                    .containsEntry("document", reference)
                    .containsEntry("path", "/dest/file.txt")
                    .doesNotContainKey("id");
              });
    }

    @Test
    void importDocument_omitsDocumentKey_whenIdNotInRegistry() {
      var agentContext = agentContextWithState("Sandbox_Gateway_1", "handle-xyz");
      var toolCalls =
          List.of(
              new ToolCall(
                  "call-import",
                  SandboxToolNames.IMPORT_DOCUMENT,
                  Map.of("id", "hallucinated-id")));

      var result = handler.transformToolCalls(agentContext, DocumentRegistry.empty(), toolCalls);

      assertThat(result).hasSize(1);
      assertThat(result.getFirst())
          .satisfies(
              tc -> {
                assertThat(tc.id()).isEqualTo("call-import");
                assertThat(tc.name()).isEqualTo("Sandbox_Gateway_1");
                assertThat(tc.arguments())
                    .containsEntry("operation", SandboxOperation.IMPORT_DOCUMENT)
                    .containsEntry("handle", "handle-xyz")
                    .doesNotContainKey("document")
                    .doesNotContainKey("id");
              });
    }

    @Test
    void importDocument_omitsPathKey_whenNotSuppliedByLlm() {
      var reference = new CamundaDocumentReferenceModel("store-1", "doc-id-1", null, null);
      var entry = new DocumentRegistryEntry("known-doc-id", reference, null, null);
      var registry = DocumentRegistry.of(List.of(entry));
      var agentContext = agentContextWithState("Sandbox_Gateway_1", "handle-xyz");
      // LLM supplies only id, no path
      var toolCalls =
          List.of(
              new ToolCall(
                  "call-import", SandboxToolNames.IMPORT_DOCUMENT, Map.of("id", "known-doc-id")));

      var result = handler.transformToolCalls(agentContext, registry, toolCalls);

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().arguments())
          .containsEntry("document", reference)
          .doesNotContainKey("path")
          .doesNotContainKey("id");
    }
  }

  // -------------------------------------------------------------------------
  // Transform tool call results
  // -------------------------------------------------------------------------

  @Nested
  class TransformToolCallResults {

    @Test
    void setsElementId_whenResultNameMatchesSandboxElementId() {
      var sandboxState = SandboxState.builder().elementId("Sandbox_Gateway_1").build();
      var agentContext = AgentContext.empty().withProperty(PROPERTY_SANDBOX, sandboxState);
      var result =
          ToolCallResult.builder().id("call-1").name("Sandbox_Gateway_1").content("output").build();

      var transformed = handler.transformToolCallResults(agentContext, List.of(result));

      assertThat(transformed).hasSize(1);
      assertThat(transformed.getFirst().elementId()).isEqualTo("Sandbox_Gateway_1");
    }

    @Test
    void passesThrough_resultNotMatchingSandboxElementId() {
      var sandboxState = SandboxState.builder().elementId("Sandbox_Gateway_1").build();
      var agentContext = AgentContext.empty().withProperty(PROPERTY_SANDBOX, sandboxState);
      var result =
          ToolCallResult.builder().id("call-2").name("other_tool").content("output").build();

      var transformed = handler.transformToolCallResults(agentContext, List.of(result));

      assertThat(transformed.getFirst()).isEqualTo(result);
    }

    @Test
    void passesThrough_whenNoSandboxTracked() {
      var agentContext = AgentContext.empty();
      var result =
          ToolCallResult.builder().id("call-1").name("Sandbox_Gateway_1").content("output").build();

      var transformed = handler.transformToolCallResults(agentContext, List.of(result));

      assertThat(transformed.getFirst()).isEqualTo(result);
    }
  }

  // -------------------------------------------------------------------------
  // Extract documents
  // -------------------------------------------------------------------------

  @Nested
  class ExtractDocuments {

    // Use document-aware ObjectMapper so convertValue can deserialize a reference map to Document
    private final ObjectMapper documentObjectMapper = TestObjectMapperSupplier.getInstance();
    private final DocumentFactoryImpl documentFactory =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
    private SandboxGatewayToolHandler documentHandler;

    @BeforeEach
    void setUpDocumentHandler() {
      documentHandler = new SandboxGatewayToolHandler(documentObjectMapper);
    }

    @Test
    void extractsDocumentFromExportResultMap() {
      // Create a real Document and convert it to a reference map (as the runtime would do after
      // round-tripping through Zeebe)
      Document doc =
          documentFactory.create(
              DocumentCreationRequest.from("hello".getBytes(StandardCharsets.UTF_8))
                  .fileName("export.bin")
                  .contentType("application/octet-stream")
                  .build());
      // Serialize via Document (not doc.reference()) so DocumentSerializer adds the discriminator
      // key "camunda.document.type", which the deserializer needs to identify a document reference.
      Map<?, ?> referenceMap = documentObjectMapper.convertValue(doc, Map.class);
      Map<String, Object> content =
          Map.of(
              "summary",
              "Exported 'export.bin' (5 bytes) as a document.",
              "document",
              referenceMap);
      var toolCallResult =
          ToolCallResult.builder().id("call-1").name("Sandbox_Gateway_1").content(content).build();

      var documents = documentHandler.extractDocuments(toolCallResult);

      assertThat(documents).hasSize(1);
      assertThat(documents.getFirst()).isInstanceOf(Document.class);
    }

    @Test
    void returnsEmptyList_whenContentIsString() {
      var toolCallResult =
          ToolCallResult.builder()
              .id("call-1")
              .name("Sandbox_Gateway_1")
              .content("Imported 'file.txt' (42 bytes) to /dest/file.txt.")
              .build();

      var documents = documentHandler.extractDocuments(toolCallResult);

      assertThat(documents).isEmpty();
    }

    @Test
    void returnsEmptyList_whenMapHasNoDocumentKey() {
      var toolCallResult =
          ToolCallResult.builder()
              .id("call-1")
              .name("Sandbox_Gateway_1")
              .content(Map.of("summary", "some output"))
              .build();

      var documents = documentHandler.extractDocuments(toolCallResult);

      assertThat(documents).isEmpty();
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private GatewayToolDefinition createGatewayToolDefinition(String type, String name) {
    return GatewayToolDefinition.builder()
        .type(type)
        .name(name)
        .description("Description for " + name)
        .properties(Map.of())
        .build();
  }
}
