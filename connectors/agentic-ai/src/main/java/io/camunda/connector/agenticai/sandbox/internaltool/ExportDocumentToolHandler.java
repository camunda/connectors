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
import io.camunda.connector.agenticai.sandbox.spi.FileInfo;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the {@code export_document} internal tool (T10 — Document export OUT): reads a file
 * from the sandbox filesystem, mints a Camunda {@link Document}, and returns it in the tool result
 * so the document-extraction path surfaces it into the conversation and final {@code
 * AgentResponse}.
 *
 * <p>The raw {@link Document} is placed directly in the content {@link List} so that {@code
 * ContentTreeDocumentWalker} can find it; the turn composer re-wraps it into a proper document
 * user-message for the LLM.
 */
public class ExportDocumentToolHandler implements InternalToolHandler {

  /** Default maximum file size (25 MB) that may be exported as a document. */
  public static final long DEFAULT_MAX_DOCUMENT_BYTES = 25L * 1024 * 1024;

  private final DocumentFactory documentFactory;
  private final long maxDocumentBytes;
  private final ToolDefinition definition;

  public ExportDocumentToolHandler(DocumentFactory documentFactory) {
    this(documentFactory, DEFAULT_MAX_DOCUMENT_BYTES);
  }

  public ExportDocumentToolHandler(DocumentFactory documentFactory, long maxDocumentBytes) {
    this.documentFactory = documentFactory;
    this.maxDocumentBytes = maxDocumentBytes;
    this.definition = buildDefinition();
  }

  @Override
  public String name() {
    return InternalToolNames.EXPORT_DOCUMENT;
  }

  @Override
  public ToolDefinition definition() {
    return definition;
  }

  @Override
  public ToolCallResult execute(
      ToolCall toolCall, SandboxSession session, InternalToolContext context) {
    String path = (String) toolCall.arguments().get("path");
    if (path == null || path.isBlank()) {
      return errorResult(toolCall, "Missing required argument: path");
    }

    FileInfo info;
    try {
      info = session.fs().stat(path);
    } catch (Exception e) {
      return errorResult(
          toolCall, e.getMessage() != null ? e.getMessage() : "stat failed: " + path);
    }

    if (info.size() > maxDocumentBytes) {
      return errorResult(
          toolCall,
          "File '%s' is too large to export (%d bytes; limit is %d bytes)."
              .formatted(path, info.size(), maxDocumentBytes));
    }

    byte[] bytes;
    try {
      bytes = session.fs().read(path);
    } catch (Exception e) {
      return errorResult(
          toolCall, e.getMessage() != null ? e.getMessage() : "read failed: " + path);
    }

    String contentType =
        info.contentType() != null ? info.contentType() : "application/octet-stream";
    String fileName = extractFileName(path);

    Document doc;
    try {
      doc =
          documentFactory.create(
              DocumentCreationRequest.from(bytes)
                  .contentType(contentType)
                  .fileName(fileName)
                  .build());
    } catch (Exception e) {
      return errorResult(
          toolCall,
          "Failed to create document for '%s': %s"
              .formatted(
                  path, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
    }

    String summary =
        "Exported '%s' (%d bytes, %s) as a document.".formatted(path, bytes.length, contentType);

    return ToolCallResult.builder()
        .id(toolCall.id())
        .name(toolCall.name())
        .content(List.of(summary, doc))
        .properties(
            Map.of(
                InternalToolExecutor.PROPERTY_EXECUTED_BY,
                InternalToolExecutor.EXECUTED_BY_SANDBOX))
        .build();
  }

  /** Returns the last path segment (basename), or the full path if no separator is present. */
  private static String extractFileName(String path) {
    int lastSlash = path.lastIndexOf('/');
    return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
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
    Map<String, Object> pathProp = new LinkedHashMap<>();
    pathProp.put(PROPERTY_TYPE, TYPE_STRING);
    pathProp.put(
        PROPERTY_DESCRIPTION,
        "Absolute path of the file to export within the sandbox. Use a path produced by your "
            + "earlier tool calls (e.g. a file you created with sandbox_fs_write or sandbox_bash); these live under "
            + "the sandbox working directory.");

    Map<String, Object> properties = Map.of("path", pathProp);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(PROPERTY_PROPERTIES, properties);
    schema.put(PROPERTY_REQUIRED, List.of("path"));

    return ToolDefinition.builder()
        .name(InternalToolNames.EXPORT_DOCUMENT)
        .description(
            "Export a file from the sandbox workspace as a Camunda Document. "
                + "The file is uploaded to Camunda document storage and then attached to the conversation as a user message "
                + "(so you can reference its contents in later steps). "
                + "Supports any file type (text or binary). "
                + "Files over the size limit cannot be exported.")
        .inputSchema(schema)
        .metadata(Map.of(ToolDefinition.METADATA_SANDBOX_TOOL, true))
        .build();
  }
}
