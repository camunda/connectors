/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai;

import org.jspecify.annotations.Nullable;

/** Typed interpretation of the opaque {@code provider} capability bag for the OpenAI family. */
public record OpenAiProviderCapabilities(@Nullable OpenAiReasoningCapabilities reasoning) {}
