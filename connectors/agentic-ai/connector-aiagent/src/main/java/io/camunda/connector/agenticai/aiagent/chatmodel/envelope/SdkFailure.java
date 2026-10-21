/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.envelope;

import org.jspecify.annotations.Nullable;

/**
 * A failure reported by a provider SDK, reduced to what the {@link ModelCallEnvelope} needs to
 * build the connector error.
 *
 * @param statusCode the HTTP status code of the provider's response
 * @param type the provider's name for the failure (error type or code), if it names one
 * @param message the provider's description of the failure, if it gives one
 * @param contextWindowExceeded whether the provider rejected the request because it exceeds the
 *     model's context window
 */
public record SdkFailure(
    int statusCode,
    @Nullable String type,
    @Nullable String message,
    boolean contextWindowExceeded) {}
