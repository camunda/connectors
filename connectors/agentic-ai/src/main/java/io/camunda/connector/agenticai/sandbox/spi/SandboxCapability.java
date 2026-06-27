/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.spi;

public enum SandboxCapability {
  FS_PERSIST_ACROSS_CONNECTIONS,
  PAUSE_RESUME_FS_ONLY,
  PAUSE_RESUME_MEMORY_STATE,
  SNAPSHOT,
  FORK,
  CUSTOM_TEMPLATE,
  FILE_SEARCH,
  STREAMING_EXEC,
  SELF_HOSTABLE
}
