/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.commons.lang3.StringUtils;

/**
 * Derives the {@code historyItemId} of a batched agent-instance history item, so a retried write
 * dedups against the previous attempt instead of duplicating it. {@code USER}/{@code ASSISTANT}
 * items key off the domain message's own self-generated id (ADR 012). {@code TOOL_RESULT} items key
 * off the originating tool-call id, so a cross-write retry (e.g. a streamed report followed later
 * by the batch write for the same turn) dedups for free; a result without an id (an event result)
 * falls back to a deterministic hash of its element id and completion timestamp.
 */
public final class AgentInstanceHistoryItemIds {

  private AgentInstanceHistoryItemIds() {}

  public static String forMessage(Message message) {
    return message.id().toString();
  }

  public static String forToolCallResult(ToolCallResultContent result) {
    if (StringUtils.isNotBlank(result.id())) {
      return result.id();
    }
    var input = result.elementId() + " " + result.completedAt();
    try {
      var digest =
          MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
