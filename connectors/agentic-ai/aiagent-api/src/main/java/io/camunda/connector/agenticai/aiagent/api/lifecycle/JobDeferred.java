/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.util.List;

/**
 * The job completes without a turn because tool results are still missing. Nothing is persisted.
 */
@Experimental
public record JobDeferred(List<ToolCallResult> arrivedResults) {}
