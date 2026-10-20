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
 * The agent work of the job threw. The handler rethrows the same exception afterwards.
 *
 * @param cause the failure of the job
 * @param errorCode the error code of a connector exception, absent for other failures
 */
@Experimental
public record JobFailed(Throwable cause, @Nullable String errorCode) {}
