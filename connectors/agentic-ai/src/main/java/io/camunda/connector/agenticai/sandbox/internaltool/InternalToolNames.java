/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

/** Central constants for all LLM-facing internal tool names. */
public final class InternalToolNames {

  private InternalToolNames() {}

  /** Run a shell command via {@code bash -lc}. Primary execution primitive. */
  public static final String BASH = "sandbox_bash";

  /** Read a file from the sandbox filesystem. Returns text or a binary/size marker. */
  public static final String FS_READ = "sandbox_fs_read";

  /** Write text content to a file in the sandbox filesystem. */
  public static final String FS_WRITE = "sandbox_fs_write";

  /**
   * Export a workspace file as a Camunda Document (OUT direction). Reserved for T10 — {@code
   * sandbox_export_document} handler is wired separately.
   */
  public static final String EXPORT_DOCUMENT = "sandbox_export_document";

  /**
   * Materialize a skill bundle into the sandbox filesystem and return its instructions. Reserved
   * for T7 — {@code sandbox_load_skill} handler is wired separately.
   */
  public static final String LOAD_SKILL = "sandbox_load_skill";

  /**
   * Materialize an in-context document (addressed by its {@code <doc id="…"/>} handle) into the
   * sandbox filesystem. IN counterpart to {@link #EXPORT_DOCUMENT} — T12.
   */
  public static final String IMPORT_DOCUMENT = "sandbox_import_document";
}
