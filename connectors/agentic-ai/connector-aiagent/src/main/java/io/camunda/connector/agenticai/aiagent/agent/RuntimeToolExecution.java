/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.time.OffsetDateTime;

/**
 * The execution of one runtime tool call.
 *
 * @param call the call that was executed
 * @param result the tool call result that becomes part of the conversation
 * @param startedAt when the execution of the call started
 * @param completedAt when the execution of the call completed
 */
public record RuntimeToolExecution(
    ToolCall call,
    ToolCallResultContent result,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt) {}
