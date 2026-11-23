/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A tool call result consumed by a turn, with the tool call that originated it when it can be
 * resolved. {@code call().resultSource()} tells who produced the result: the process, a runtime
 * tool, or the rejection of the call. The time the tool completed is {@code
 * result().completedAt()}.
 *
 * @param call the originating tool call, absent when it cannot be correlated
 * @param cancelled whether the tool was interrupted instead of completed
 * @param startedAt when the agent started to execute a runtime tool; absent for a process tool,
 *     whose start the connector does not see, and for a rejected call, which is not executed
 */
@Experimental
public record ToolExecution(
    @Nullable ToolCall call,
    ToolCallResultContent result,
    boolean cancelled,
    @Nullable OffsetDateTime startedAt) {}
