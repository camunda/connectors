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
import org.jspecify.annotations.Nullable;

/**
 * A tool call result consumed by a turn, with the tool call that originated it when it can be
 * resolved.
 *
 * @param call the originating tool call, absent when it cannot be correlated
 * @param cancelled whether the tool was interrupted instead of completed
 */
@Experimental
public record ToolExecution(
    @Nullable ToolCall call, ToolCallResultContent result, boolean cancelled) {}
