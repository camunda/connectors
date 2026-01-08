/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import java.util.List;

public record Prompt(String name, String description, List<PromptArgument> arguments) {

  public record PromptArgument(String name, String description, boolean required) {}
}
