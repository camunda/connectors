/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Universal response-format hint passed via {@link ChatOptions#responseFormat()}. {@link Text}
 * requests free-form text output; {@link Json} requests JSON, optionally constrained by a JSON
 * Schema. {@code null} on {@link ChatOptions} leaves the provider default in place — this matches
 * today's behaviour where no explicit format is sent for the text case.
 *
 * <p>The {@link Json#schema()} payload is the JSON Schema wire format ({@code Map<String, Object>})
 * as supplied by the connector configuration; implementations translate it onto the provider's
 * native shape (e.g. OpenAI {@code response_format.json_schema}, Google {@code responseSchema}).
 * Providers without a native structured-output mode (Anthropic Messages today) treat {@link Json}
 * as best-effort and rely on the system prompt to constrain output.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
 */
public sealed interface ResponseFormat permits ResponseFormat.Text, ResponseFormat.Json {

  record Text() implements ResponseFormat {}

  record Json(@Nullable String schemaName, @Nullable Map<String, Object> schema)
      implements ResponseFormat {}
}
