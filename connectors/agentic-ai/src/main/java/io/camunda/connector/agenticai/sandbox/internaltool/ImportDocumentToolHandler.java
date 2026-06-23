/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the {@code sandbox_import_document} internal tool (T12 — Document import IN): resolves
 * an in-context document by its registry handle ({@code <doc id="…"/>}), downloads the bytes via
 * {@link DocumentFactory#resolve}, and writes them into the sandbox filesystem so that {@code
 * sandbox_bash} / {@code sandbox_fs_read} can operate on the content.
 *
 * <p>This is the IN counterpart to {@link ExportDocumentToolHandler} (OUT). Security model: only
 * documents present in this execution's {@link
 * io.camunda.connector.agenticai.model.document.DocumentRegistry} are importable — the agent
 * supplies a handle, not an address, and the registry is populated exclusively by the runtime
 * (engine-driven, §11.6).
 *
 * <p><b>Scope note:</b> The registry is built from the loaded store state plus the current
 * invocation's input messages, before the sub-loop starts. Documents minted by {@code
 * sandbox_export_document} <em>within</em> the same sub-loop are therefore not importable in that
 * same invocation. This is acceptable for T12 and flagged as a follow-up.
 */
public class ImportDocumentToolHandler implements InternalToolHandler {

  /** Default maximum document size (25 MB) that may be imported into the sandbox. */
  public static final long DEFAULT_MAX_DOCUMENT_BYTES =
      ExportDocumentToolHandler.DEFAULT_MAX_DOCUMENT_BYTES;

  private final DocumentFactory documentFactory;
  private final long maxDocumentBytes;
  private final ToolDefinition definition;

  public ImportDocumentToolHandler(DocumentFactory documentFactory) {
    this(documentFactory, DEFAULT_MAX_DOCUMENT_BYTES);
  }

  public ImportDocumentToolHandler(DocumentFactory documentFactory, long maxDocumentBytes) {
    this.documentFactory = documentFactory;
    this.maxDocumentBytes = maxDocumentBytes;
    this.definition = buildDefinition();
  }

  @Override
  public String name() {
    return InternalToolNames.IMPORT_DOCUMENT;
  }

  @Override
  public ToolDefinition definition() {
    return definition;
  }

  @Override
  public ToolCallResult execute(
      ToolCall toolCall, SandboxSession session, InternalToolContext context) {
    final String id = (String) toolCall.arguments().get("id");
    if (id == null || id.isBlank()) {
      return errorResult(toolCall, "Missing required argument: id");
    }

    // Registry-only resolution — never fetch an agent-supplied address (§11.6).
    final var entry = context.documentRegistry().findById(id).orElse(null);
    if (entry == null) {
      final var available =
          context.documentRegistry().entries().stream()
              .map(e -> e.id() + (e.fileName() != null ? " (" + e.fileName() + ")" : ""))
              .toList();
      final String availableStr = available.isEmpty() ? "(none)" : String.join(", ", available);
      return errorResult(
          toolCall,
          "Document id '%s' is not in the registry. Available handles: %s."
              .formatted(id, availableStr));
    }

    // Resolve the document reference to a Document, then download its bytes.
    final byte[] bytes;
    try {
      final var doc = documentFactory.resolve(entry.reference());
      bytes = doc.asByteArray();
    } catch (Exception e) {
      return errorResult(
          toolCall,
          "Failed to resolve document '%s': %s"
              .formatted(
                  id, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
    }

    if (bytes.length > maxDocumentBytes) {
      return errorResult(
          toolCall,
          "Document '%s' is too large to import (%d bytes; limit is %d bytes)."
              .formatted(id, bytes.length, maxDocumentBytes));
    }

    // Determine target path: explicit path > <workDir>/<fileName> > <workDir>/<id>
    final String fileName =
        (entry.fileName() != null && !entry.fileName().isBlank()) ? entry.fileName() : id;
    final String explicitPath = (String) toolCall.arguments().get("path");
    final String targetPath =
        (explicitPath != null && !explicitPath.isBlank())
            ? explicitPath
            : session.workDir() + "/" + fileName;

    try {
      session.fs().write(targetPath, bytes);
    } catch (Exception e) {
      return errorResult(
          toolCall,
          "Failed to write document '%s' to '%s': %s"
              .formatted(
                  id,
                  targetPath,
                  e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
    }

    final String contentType =
        (entry.contentType() != null && !entry.contentType().isBlank())
            ? entry.contentType()
            : "application/octet-stream";
    final String summary =
        "Imported '%s' (%d bytes, %s) to %s."
            .formatted(fileName, bytes.length, contentType, targetPath);

    return ToolCallResult.builder()
        .id(toolCall.id())
        .name(toolCall.name())
        .content(summary)
        .properties(
            Map.of(
                InternalToolExecutor.PROPERTY_EXECUTED_BY,
                InternalToolExecutor.EXECUTED_BY_SANDBOX))
        .build();
  }

  private static ToolCallResult errorResult(ToolCall toolCall, String message) {
    return ToolCallResult.builder()
        .id(toolCall.id())
        .name(toolCall.name())
        .content("Error: " + message)
        .properties(
            Map.of(
                InternalToolExecutor.PROPERTY_EXECUTED_BY,
                InternalToolExecutor.EXECUTED_BY_SANDBOX))
        .build();
  }

  private static ToolDefinition buildDefinition() {
    Map<String, Object> idProp = new LinkedHashMap<>();
    idProp.put(PROPERTY_TYPE, TYPE_STRING);
    idProp.put(
        PROPERTY_DESCRIPTION,
        "The id from a <doc id=\"…\"/> marker in the conversation. "
            + "Use the exact id value as it appears in the marker.");

    Map<String, Object> pathProp = new LinkedHashMap<>();
    pathProp.put(PROPERTY_TYPE, TYPE_STRING);
    pathProp.put(
        PROPERTY_DESCRIPTION,
        "Target path in the sandbox filesystem where the document should be written. "
            + "If omitted, the file is written to <workDir>/<fileName> (using the document's original file name).");

    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("id", idProp);
    properties.put("path", pathProp);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(PROPERTY_PROPERTIES, properties);
    schema.put(PROPERTY_REQUIRED, List.of("id"));

    return ToolDefinition.builder()
        .name(InternalToolNames.IMPORT_DOCUMENT)
        .description(
            "Import a document from the conversation context into the sandbox filesystem. "
                + "Use the id from a <doc id=\"…\"/> marker to identify the document. "
                + "After importing, the file can be read with sandbox_fs_read or processed with sandbox_bash. "
                + "Documents over the size limit cannot be imported.")
        .inputSchema(schema)
        .metadata(Map.of(ToolDefinition.METADATA_SANDBOX_TOOL, true))
        .build();
  }
}
