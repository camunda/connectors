/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.discovery;

import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed schema definitions for the five sandbox gateway tools that are injected into the tool list
 * when a sandbox gateway element is present in the ad-hoc sub-process.
 */
public final class SandboxToolDefinitions {

  private SandboxToolDefinitions() {}

  /** Metadata key carrying the sandbox operation (a {@link SandboxOperation} enum value). */
  public static final String METADATA_OPERATION = "operation";

  /** Metadata key carrying the sandbox handle (opaque identifier for the provisioned sandbox). */
  public static final String METADATA_HANDLE = "handle";

  /**
   * Metadata key carrying the sandbox working directory (the base filesystem path within the
   * sandbox).
   */
  public static final String METADATA_WORK_DIR = "workDir";

  /**
   * Metadata key carrying the skill catalog entries available in the sandbox (may be null/absent if
   * the sandbox has no skills configured).
   */
  public static final String METADATA_CATALOG = "catalog";

  /**
   * Returns the list of {@link ToolDefinition}s for all sandbox gateway tools, each tagged with
   * sandbox metadata pointing at the given BPMN element id.
   *
   * @param elementId the BPMN element id of the sandbox gateway element
   * @return list of five sandbox tool definitions
   */
  public static List<ToolDefinition> sandboxToolDefinitions(String elementId) {
    final var baseMetadata = new LinkedHashMap<String, Object>();
    baseMetadata.put(ToolDefinition.METADATA_GATEWAY_TYPE, SandboxGatewayToolHandler.GATEWAY_TYPE);
    baseMetadata.put(ToolDefinition.METADATA_ELEMENT_ID, elementId);
    return List.of(
        bashDefinition(withOperation(baseMetadata, SandboxOperation.BASH)),
        fsReadDefinition(withOperation(baseMetadata, SandboxOperation.FS_READ)),
        fsWriteDefinition(withOperation(baseMetadata, SandboxOperation.FS_WRITE)),
        exportDocumentDefinition(withOperation(baseMetadata, SandboxOperation.EXPORT_DOCUMENT)),
        importDocumentDefinition(withOperation(baseMetadata, SandboxOperation.IMPORT_DOCUMENT)));
  }

  /**
   * Returns the list of {@link ToolDefinition}s for all sandbox gateway tools, each tagged with
   * sandbox metadata including the sandbox handle, working directory, and optional skill catalog.
   *
   * @param elementId the BPMN element id of the sandbox gateway element
   * @param handle the opaque sandbox handle returned by the CREATE operation
   * @param workDir the sandbox working directory returned by the CREATE operation
   * @param catalog optional skill catalog returned by the CREATE operation (may be null)
   * @return list of five sandbox tool definitions
   */
  public static List<ToolDefinition> sandboxToolDefinitions(
      String elementId, String handle, String workDir, List<SkillCatalogEntry> catalog) {
    final var baseMetadata = new LinkedHashMap<String, Object>();
    baseMetadata.put(ToolDefinition.METADATA_GATEWAY_TYPE, SandboxGatewayToolHandler.GATEWAY_TYPE);
    baseMetadata.put(ToolDefinition.METADATA_ELEMENT_ID, elementId);
    baseMetadata.put(METADATA_HANDLE, handle);
    baseMetadata.put(METADATA_WORK_DIR, workDir);
    if (catalog != null && !catalog.isEmpty()) {
      baseMetadata.put(METADATA_CATALOG, catalog);
    }
    return List.of(
        bashDefinition(withOperation(baseMetadata, SandboxOperation.BASH)),
        fsReadDefinition(withOperation(baseMetadata, SandboxOperation.FS_READ)),
        fsWriteDefinition(withOperation(baseMetadata, SandboxOperation.FS_WRITE)),
        exportDocumentDefinition(withOperation(baseMetadata, SandboxOperation.EXPORT_DOCUMENT)),
        importDocumentDefinition(withOperation(baseMetadata, SandboxOperation.IMPORT_DOCUMENT)));
  }

  private static Map<String, Object> withOperation(
      Map<String, Object> baseMetadata, SandboxOperation operation) {
    final var copy = new LinkedHashMap<>(baseMetadata);
    copy.put(METADATA_OPERATION, operation);
    return copy;
  }

  private static ToolDefinition bashDefinition(Map<String, Object> metadata) {
    Map<String, Object> commandProp = new LinkedHashMap<>();
    commandProp.put(PROPERTY_TYPE, TYPE_STRING);
    commandProp.put(
        PROPERTY_DESCRIPTION,
        "Shell command to run via bash -lc. Supports pipes, redirects, globs.");

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(PROPERTY_PROPERTIES, Map.of("command", commandProp));
    schema.put(PROPERTY_REQUIRED, List.of("command"));

    return ToolDefinition.builder()
        .name(SandboxToolNames.BASH)
        .description(
            "Run a shell command in the sandbox via bash -lc. Returns combined stdout/stderr output"
                + " and the exit code. Stateless per call — workspace filesystem persists but shell"
                + " state (cwd, env vars, background jobs) does not.")
        .inputSchema(schema)
        .metadata(metadata)
        .build();
  }

  private static ToolDefinition fsReadDefinition(Map<String, Object> metadata) {
    Map<String, Object> pathProp = new LinkedHashMap<>();
    pathProp.put(PROPERTY_TYPE, TYPE_STRING);
    pathProp.put(PROPERTY_DESCRIPTION, "Absolute path to the file to read.");

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(PROPERTY_PROPERTIES, Map.of("path", pathProp));
    schema.put(PROPERTY_REQUIRED, List.of("path"));

    return ToolDefinition.builder()
        .name(SandboxToolNames.FS_READ)
        .description(
            "Read a file from the sandbox filesystem. Returns the file's text content for UTF-8"
                + " text files. Binary files or files over the size limit return a marker — use"
                + " sandbox_export_document to retrieve them as Camunda Documents.")
        .inputSchema(schema)
        .metadata(metadata)
        .build();
  }

  private static ToolDefinition fsWriteDefinition(Map<String, Object> metadata) {
    Map<String, Object> pathProp = new LinkedHashMap<>();
    pathProp.put(PROPERTY_TYPE, TYPE_STRING);
    pathProp.put(PROPERTY_DESCRIPTION, "Absolute path to write the file to.");

    Map<String, Object> contentProp = new LinkedHashMap<>();
    contentProp.put(PROPERTY_TYPE, TYPE_STRING);
    contentProp.put(PROPERTY_DESCRIPTION, "UTF-8 text content to write.");

    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", pathProp);
    properties.put("content", contentProp);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(PROPERTY_PROPERTIES, properties);
    schema.put(PROPERTY_REQUIRED, List.of("path", "content"));

    return ToolDefinition.builder()
        .name(SandboxToolNames.FS_WRITE)
        .description(
            "Write UTF-8 text content to a file in the sandbox filesystem. Creates or overwrites"
                + " the file at the given path. Parent directory creation is handled by the"
                + " filesystem provider.")
        .inputSchema(schema)
        .metadata(metadata)
        .build();
  }

  private static ToolDefinition exportDocumentDefinition(Map<String, Object> metadata) {
    Map<String, Object> pathProp = new LinkedHashMap<>();
    pathProp.put(PROPERTY_TYPE, TYPE_STRING);
    pathProp.put(
        PROPERTY_DESCRIPTION,
        "Absolute path of the file to export within the sandbox. Use a path produced by your"
            + " earlier tool calls (e.g. a file you created with sandbox_fs_write or sandbox_bash);"
            + " these live under the sandbox working directory.");

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(PROPERTY_PROPERTIES, Map.of("path", pathProp));
    schema.put(PROPERTY_REQUIRED, List.of("path"));

    return ToolDefinition.builder()
        .name(SandboxToolNames.EXPORT_DOCUMENT)
        .description(
            "Export a file from the sandbox workspace as a Camunda Document. The file is uploaded"
                + " to Camunda document storage and then attached to the conversation as a user"
                + " message (so you can reference its contents in later steps). Supports any file"
                + " type (text or binary). Files over the size limit cannot be exported.")
        .inputSchema(schema)
        .metadata(metadata)
        .build();
  }

  private static ToolDefinition importDocumentDefinition(Map<String, Object> metadata) {
    Map<String, Object> idProp = new LinkedHashMap<>();
    idProp.put(PROPERTY_TYPE, TYPE_STRING);
    idProp.put(
        PROPERTY_DESCRIPTION,
        "The id from a <doc id=\"…\"/> marker in the conversation. Use the exact id value as"
            + " it appears in the marker.");

    Map<String, Object> pathProp = new LinkedHashMap<>();
    pathProp.put(PROPERTY_TYPE, TYPE_STRING);
    pathProp.put(
        PROPERTY_DESCRIPTION,
        "Target path in the sandbox filesystem where the document should be written. If omitted,"
            + " the file is written to <workDir>/<fileName> (using the document's original file"
            + " name).");

    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("id", idProp);
    properties.put("path", pathProp);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(PROPERTY_PROPERTIES, properties);
    schema.put(PROPERTY_REQUIRED, List.of("id"));

    return ToolDefinition.builder()
        .name(SandboxToolNames.IMPORT_DOCUMENT)
        .description(
            "Import a document from the conversation context into the sandbox filesystem. Use the"
                + " id from a <doc id=\"…\"/> marker to identify the document. After"
                + " importing, the file can be read with sandbox_fs_read or processed with"
                + " sandbox_bash. Documents over the size limit cannot be imported.")
        .inputSchema(schema)
        .metadata(metadata)
        .build();
  }
}
