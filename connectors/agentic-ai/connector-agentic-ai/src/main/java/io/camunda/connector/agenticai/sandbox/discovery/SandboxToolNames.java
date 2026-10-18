/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.discovery;

/** Fixed tool names for sandbox gateway tools exposed to the LLM. */
public final class SandboxToolNames {

  private SandboxToolNames() {}

  /** Tool name for the bash execution tool: runs a shell command via {@code bash -lc}. */
  public static final String BASH = "sandbox_bash";

  /** Tool name for the filesystem read tool: reads a file from the sandbox filesystem. */
  public static final String FS_READ = "sandbox_fs_read";

  /** Tool name for the filesystem write tool: writes a file to the sandbox filesystem. */
  public static final String FS_WRITE = "sandbox_fs_write";

  /**
   * Tool name for the export-document tool: uploads a sandbox file as a Camunda Document and
   * attaches it to the conversation.
   */
  public static final String EXPORT_DOCUMENT = "sandbox_export_document";

  /**
   * Tool name for the import-document tool: downloads a document from the conversation context into
   * the sandbox filesystem.
   */
  public static final String IMPORT_DOCUMENT = "sandbox_import_document";

  /**
   * Reserved prefix for all sandbox gateway tool names. Modeled BPMN tool activities must not use
   * names starting with this prefix.
   */
  public static final String RESERVED_PREFIX = "sandbox_";
}
