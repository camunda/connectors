/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import io.camunda.connector.api.document.Document;
import org.jspecify.annotations.Nullable;

/**
 * A document that entered the conversation during turn composition, together with the tool call
 * that produced it. {@code toolCallId} and {@code toolName} are {@code null} for user prompt and
 * event documents.
 */
public record IngestedDocument(
    Document document, @Nullable String toolCallId, @Nullable String toolName) {}
