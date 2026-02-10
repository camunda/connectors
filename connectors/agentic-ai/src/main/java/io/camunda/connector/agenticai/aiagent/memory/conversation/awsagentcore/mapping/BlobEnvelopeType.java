/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enumeration of supported blob envelope types for AWS AgentCore Memory.
 *
 * <p>All structured data (non-conversational text) is wrapped in typed envelopes with a blobType
 * discriminator for consistent deserialization.
 */
public enum BlobEnvelopeType {
  /** Envelope for ToolCall[] arrays from AssistantMessage */
  TOOL_CALLS("camunda.toolCalls"),

  /** Envelope for ToolCallResult[] arrays from ToolCallResultMessage */
  TOOL_CALL_RESULTS("camunda.toolCallResults"),

  /** Envelope for non-text Content objects (DocumentContent, BlobContent, etc.) */
  MESSAGE_CONTENT("camunda.messageContent");

  private final String blobType;

  BlobEnvelopeType(String blobType) {
    this.blobType = blobType;
  }

  public String getBlobType() {
    return blobType;
  }

  /**
   * Find the enum constant matching the given blobType string.
   *
   * @param blobType the blobType string from a blob payload
   * @return Optional containing the matching enum constant, or empty if not found
   */
  public static Optional<BlobEnvelopeType> fromString(String blobType) {
    return Arrays.stream(values()).filter(type -> type.blobType.equals(blobType)).findFirst();
  }
}
