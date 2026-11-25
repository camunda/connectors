/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;

/**
 * The chat model call of a turn returned.
 *
 * @param iterationKey the iteration key of the turn
 * @param assistantMessage the response of the model after the admission of its tool calls: each
 *     tool call carries its kind and, for a call that is not carried out, its rejection
 * @param metrics the metrics of the turn
 * @param anotherTurnFollows whether the job calls the model again, because the provider paused the
 *     response or because the model called only tools that the agent answers itself
 */
@Experimental
public record TurnCompleted(
    int iterationKey,
    AssistantMessage assistantMessage,
    AgentMetrics metrics,
    boolean anotherTurnFollows) {}
