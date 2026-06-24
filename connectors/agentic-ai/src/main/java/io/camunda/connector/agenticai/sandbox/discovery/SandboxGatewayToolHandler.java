/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDefinitionUpdates;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolDiscoveryInitiationResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.util.CollectionUtils;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import jakarta.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gateway tool handler for the sandbox integration.
 *
 * <p>The sandbox is modelled as a BPMN gateway element that handles multiple fixed tools ({@code
 * sandbox_bash}, {@code sandbox_fs_read}, etc.). On first entry the handler emits a CREATE
 * discovery call; the sandbox element provisions the sandbox and returns its {@link
 * SandboxCreateResult}. Subsequent tool calls are rewritten to carry the sandbox handle and the
 * matching {@link SandboxOperation}.
 */
public class SandboxGatewayToolHandler implements GatewayToolHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(SandboxGatewayToolHandler.class);

  public static final String GATEWAY_TYPE = "sandbox";
  public static final String PROPERTY_SANDBOX = "sandbox";
  public static final String SANDBOX_DISCOVERY_PREFIX = "SANDBOX_create_";

  private static final String ERROR_CODE_SANDBOX_MULTIPLE_NOT_ALLOWED =
      "SANDBOX_MULTIPLE_NOT_ALLOWED";

  private final ObjectMapper objectMapper;

  public SandboxGatewayToolHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public String type() {
    return GATEWAY_TYPE;
  }

  @Override
  public boolean isGatewayManaged(String toolName) {
    return toolName != null && toolName.startsWith(SandboxToolNames.RESERVED_PREFIX);
  }

  /**
   * NOTE: Sandbox tool names ({@code sandbox_bash}, {@code sandbox_fs_read}, etc.) do NOT encode
   * the element id — there is at most one sandbox per process. The element id cannot be derived
   * from the LLM tool name alone. This method returns the tool name unchanged so the registry's
   * {@code .orElse(result.name())} fallback is a no-op; the correct element id is set in {@link
   * #transformToolCallResults} where we DO have context ({@link #PROPERTY_SANDBOX}).
   */
  @Override
  public String resolveElementId(String toolName) {
    return toolName;
  }

  @Override
  public GatewayToolDiscoveryInitiationResult initiateToolDiscovery(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions) {
    final var sandboxDefs = extractSandboxGatewayToolDefinitions(gatewayToolDefinitions);

    if (sandboxDefs.isEmpty()) {
      return new GatewayToolDiscoveryInitiationResult(agentContext, List.of());
    }

    if (sandboxDefs.size() > 1) {
      final var ids =
          sandboxDefs.stream()
              .map(GatewayToolDefinition::name)
              .map("'%s'"::formatted)
              .collect(Collectors.joining(", "));
      throw new ConnectorException(
          ERROR_CODE_SANDBOX_MULTIPLE_NOT_ALLOWED,
          "Only one sandbox gateway element is allowed per process, but found %d: [%s]. Remove or merge the extra sandbox gateway elements."
              .formatted(sandboxDefs.size(), ids));
    }

    final var elementId = sandboxDefs.getFirst().name();
    final var updatedCtx = agentContext.withProperty(PROPERTY_SANDBOX, elementId);
    final var createOp = Map.<String, Object>of("operation", SandboxOperation.CREATE);
    final var discoveryCall =
        new ToolCall(SANDBOX_DISCOVERY_PREFIX + elementId, elementId, createOp);
    return new GatewayToolDiscoveryInitiationResult(updatedCtx, List.of(discoveryCall));
  }

  @Override
  public GatewayToolDefinitionUpdates resolveUpdatedGatewayToolDefinitions(
      AgentContext agentContext, List<GatewayToolDefinition> gatewayToolDefinitions) {
    final var currentId = getSandboxElementId(agentContext);
    final var currentList = currentId != null ? List.of(currentId) : List.<String>of();
    final var newIds =
        extractSandboxGatewayToolDefinitions(gatewayToolDefinitions).stream()
            .map(GatewayToolDefinition::name)
            .toList();
    return CollectionUtils.computeListItemChanges(
        currentList, newIds, GatewayToolDefinitionUpdates::new);
  }

  @Override
  public boolean allToolDiscoveryResultsPresent(
      AgentContext agentContext, List<ToolCallResult> toolCallResults) {
    final var elementId = getSandboxElementId(agentContext);
    if (elementId == null) {
      return true;
    }
    final var resultIds =
        toolCallResults.stream().map(ToolCallResult::id).collect(Collectors.toSet());
    return resultIds.contains(SANDBOX_DISCOVERY_PREFIX + elementId);
  }

  @Override
  public boolean handlesToolDiscoveryResult(ToolCallResult toolCallResult) {
    return toolCallResult.id() != null && toolCallResult.id().startsWith(SANDBOX_DISCOVERY_PREFIX);
  }

  @Override
  public List<ToolDefinition> handleToolDiscoveryResults(
      AgentContext agentContext, List<ToolCallResult> toolDiscoveryResults) {
    return toolDiscoveryResults.stream()
        .map(this::toolDefinitionsFromCreateResult)
        .flatMap(List::stream)
        .toList();
  }

  private List<ToolDefinition> toolDefinitionsFromCreateResult(ToolCallResult result) {
    final var createResult = objectMapper.convertValue(result.content(), SandboxCreateResult.class);
    // result.name() == elementId (set in initiateToolDiscovery as name=elementId)
    final var elementId = result.name();
    return SandboxToolDefinitions.sandboxToolDefinitions(
        elementId, createResult.handle(), createResult.workDir(), createResult.catalog());
  }

  @Override
  public List<ToolCall> transformToolCalls(
      AgentContext agentContext, DocumentRegistry documentRegistry, List<ToolCall> toolCalls) {
    return toolCalls.stream()
        .map(
            toolCall -> {
              if (!isGatewayManaged(toolCall.name())) {
                return toolCall;
              }
              // Find the ToolDefinition for this tool name to get metadata (elementId, handle)
              final var toolDef =
                  agentContext.toolDefinitions().stream()
                      .filter(td -> toolCall.name().equals(td.name()))
                      .findFirst()
                      .orElse(null);
              if (toolDef == null) {
                return toolCall;
              }
              final var elementId =
                  (String) toolDef.metadata().get(ToolDefinition.METADATA_ELEMENT_ID);
              final var handle =
                  (String) toolDef.metadata().get(SandboxToolDefinitions.METADATA_HANDLE);
              final var operation = toolNameToOperation(toolCall.name());
              if (elementId == null || operation == null) {
                return toolCall;
              }
              final var payload = new LinkedHashMap<String, Object>();
              payload.put("operation", operation);
              if (handle != null) {
                payload.put("handle", handle);
              }
              if (operation == SandboxOperation.IMPORT_DOCUMENT) {
                // Resolve the document registry id to a reference on the trusted in-process side.
                // The connector job has no access to the registry; resolution must happen here.
                final var id =
                    toolCall.arguments() != null ? (String) toolCall.arguments().get("id") : null;
                final var path =
                    toolCall.arguments() != null ? (String) toolCall.arguments().get("path") : null;
                final java.util.Optional<DocumentRegistryEntry> registryEntry =
                    id != null ? documentRegistry.findById(id) : java.util.Optional.empty();
                if (registryEntry.isPresent()) {
                  payload.put("document", registryEntry.get().reference());
                } else {
                  LOGGER.warn(
                      "sandbox_import_document: no document found in registry for id '{}' — the connector will return an error",
                      id);
                }
                if (path != null) {
                  payload.put("path", path);
                }
              } else {
                // merge LLM-supplied arguments (command, path, content, etc.)
                if (toolCall.arguments() != null) {
                  payload.putAll(toolCall.arguments());
                }
              }
              return new ToolCall(toolCall.id(), elementId, payload);
            })
        .toList();
  }

  @Override
  public List<ToolCallResult> transformToolCallResults(
      AgentContext agentContext, List<ToolCallResult> toolCallResults) {
    final var sandboxElementId = getSandboxElementId(agentContext);
    return toolCallResults.stream()
        .map(
            result -> {
              // Sandbox results: name() equals the sandbox element id
              if (sandboxElementId != null && sandboxElementId.equals(result.name())) {
                // Set elementId so the registry doesn't need to derive it from the name
                if (result.elementId() == null) {
                  return result.withElementId(sandboxElementId);
                }
              }
              return result;
            })
        .toList();
  }

  /**
   * Re-materializes documents exported by the sandbox connector.
   *
   * <p>The EXPORT_DOCUMENT result is a {@link Map} with a {@code "document"} key containing the
   * document reference (serialized to a reference map by the connector runtime). The generic {@link
   * io.camunda.connector.agenticai.aiagent.agent.ContentTreeDocumentWalker} cannot find real {@link
   * Document} instances inside reference maps, so this handler converts the reference map back to a
   * {@link Document} via the document-aware {@code objectMapper}.
   */
  @Override
  public List<Document> extractDocuments(ToolCallResult toolCallResult) {
    if (!(toolCallResult.content() instanceof Map<?, ?> contentMap)) {
      return GatewayToolHandler.super.extractDocuments(toolCallResult);
    }
    Object docEntry = contentMap.get("document");
    if (docEntry == null) {
      return GatewayToolHandler.super.extractDocuments(toolCallResult);
    }
    try {
      Document doc = objectMapper.convertValue(docEntry, Document.class);
      if (doc == null) {
        return List.of();
      }
      return List.of(doc);
    } catch (Exception e) {
      LOGGER.warn(
          "Failed to convert document entry to Document in sandbox tool call result (id={}, name={}): {}",
          toolCallResult.id(),
          toolCallResult.name(),
          e.getMessage());
      return List.of();
    }
  }

  private SandboxOperation toolNameToOperation(String toolName) {
    return switch (toolName) {
      case SandboxToolNames.BASH -> SandboxOperation.BASH;
      case SandboxToolNames.FS_READ -> SandboxOperation.FS_READ;
      case SandboxToolNames.FS_WRITE -> SandboxOperation.FS_WRITE;
      case SandboxToolNames.EXPORT_DOCUMENT -> SandboxOperation.EXPORT_DOCUMENT;
      case SandboxToolNames.IMPORT_DOCUMENT -> SandboxOperation.IMPORT_DOCUMENT;
      default -> null;
    };
  }

  private List<GatewayToolDefinition> extractSandboxGatewayToolDefinitions(
      List<GatewayToolDefinition> defs) {
    return defs.stream().filter(d -> GATEWAY_TYPE.equals(d.type())).toList();
  }

  @Nullable
  private String getSandboxElementId(AgentContext agentContext) {
    return (String) agentContext.properties().getOrDefault(PROPERTY_SANDBOX, null);
  }
}
