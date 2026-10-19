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
 * A call to the chat model failed.
 *
 * @param errorType a low-cardinality classification: the error code of a model rejection, else the
 *     class name of the failure
 * @param errorCode the error code of a rejection, absent for other failures
 */
@Experimental
public record ModelRoundFailed(String errorType, @Nullable String errorCode, Throwable cause) {}
