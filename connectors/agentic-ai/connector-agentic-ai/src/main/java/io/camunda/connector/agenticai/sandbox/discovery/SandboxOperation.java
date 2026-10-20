/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.discovery;

/** Operations that the sandbox gateway tool handler can dispatch to the sandbox BPMN element. */
public enum SandboxOperation {
  CREATE,
  BASH,
  FS_READ,
  FS_WRITE,
  EXPORT_DOCUMENT,
  IMPORT_DOCUMENT
}
