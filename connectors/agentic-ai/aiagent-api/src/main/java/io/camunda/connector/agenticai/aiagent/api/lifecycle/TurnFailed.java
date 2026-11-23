/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;
import org.jspecify.annotations.Nullable;

/**
 * The chat model call of a turn failed. The handler rethrows the same exception afterwards.
 *
 * @param iterationKey the iteration key of the turn
 * @param cause the failure of the call
 * @param errorCode the error code of a connector exception, absent for other failures
 */
@Experimental
public record TurnFailed(int iterationKey, Throwable cause, @Nullable String errorCode) {}
