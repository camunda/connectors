/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import io.camunda.connector.agenticai.mcp.client.model.Icon;
import java.util.List;

public record PromptDescription(
    String name, String description, List<PromptArgument> arguments, String title, List<Icon> icons) {

  public record PromptArgument(String name, String description, boolean required) {}
}
