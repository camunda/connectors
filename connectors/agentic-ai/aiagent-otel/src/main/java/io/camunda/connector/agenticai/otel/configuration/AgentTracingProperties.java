/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel.configuration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the AI Agent OpenTelemetry tracing.
 *
 * @param enabled whether the AI Agent emits OpenTelemetry spans; requires an {@code OpenTelemetry}
 *     bean, which the runtime provides through Spring Boot's OpenTelemetry support
 * @param captureContent whether prompts, tool arguments, tool results, and error messages are
 *     attached to the spans
 * @param clusterId optional prefix of the session identifier, to tell the agent instances of
 *     several clusters apart in one observability backend
 */
@ConfigurationProperties("camunda.connector.agenticai.aiagent.tracing")
public record AgentTracingProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("false") boolean captureContent,
    @Nullable String clusterId) {}
