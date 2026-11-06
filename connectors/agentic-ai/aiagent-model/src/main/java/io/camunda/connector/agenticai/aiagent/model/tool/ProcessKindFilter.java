/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

/** Jackson value filter: a {@link ToolKind#PROCESS} kind is left out of the JSON. */
final class ProcessKindFilter {

  @Override
  public boolean equals(Object other) {
    return other == null || ToolKind.PROCESS.equals(other);
  }

  @Override
  public int hashCode() {
    return ToolKind.PROCESS.hashCode();
  }
}
