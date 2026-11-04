/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.time.OffsetDateTime;

/**
 * The execution of one runtime tool call: the result for the conversation and when it was executed.
 */
public record RuntimeToolExecution(
    ToolCall call,
    ToolCallResultContent result,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt) {}
