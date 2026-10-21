/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

/**
 * How the response of the agent is shaped.
 *
 * @param format the format of the model response
 * @param includeAssistantMessage whether the full assistant message is part of the response, next
 *     to the text content
 */
public record ResponseConfiguration(
    ResponseFormatConfiguration format, boolean includeAssistantMessage) {}
