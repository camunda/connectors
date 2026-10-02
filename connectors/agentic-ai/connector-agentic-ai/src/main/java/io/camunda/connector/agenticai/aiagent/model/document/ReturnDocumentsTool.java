/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;

/**
 * The built-in {@code return_documents} tool. The model calls it to select documents for the final
 * response of the agent; the agent answers the call itself, it is never an element activation. The
 * definition is added to the tool list sent to the model when the response configuration opts in,
 * it is not part of the persisted tool definitions.
 */
public final class ReturnDocumentsTool {

  /** The reserved tool name; ad-hoc tool elements must not use it. */
  public static final String NAME = "return_documents";

  /** The array parameter carrying the ids of the {@code <doc/>} tags to return. */
  public static final String PARAMETER_IDS = "ids";

  private static final ToolDefinition DEFINITION =
      ToolDefinition.builder()
          .name(NAME)
          .description(
              "Attaches documents to your final response. Call this before you give your final"
                  + " answer for every document the response must contain. Pass the ids of the"
                  + " <doc/> tags of the documents; the ids accumulate over several calls.")
          .inputSchema(
              Map.of(
                  "type",
                  "object",
                  "properties",
                  Map.of(
                      PARAMETER_IDS,
                      Map.of(
                          "type",
                          "array",
                          "description",
                          "The ids of the <doc/> tags of the documents to return.",
                          "items",
                          Map.of("type", "string"))),
                  "required",
                  List.of(PARAMETER_IDS)))
          .documentParameters(List.of("/properties/" + PARAMETER_IDS + "/items"))
          .build();

  private ReturnDocumentsTool() {}

  public static ToolDefinition definition() {
    return DEFINITION;
  }
}
