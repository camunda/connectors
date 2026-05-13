/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.strategy;

import io.camunda.connector.agenticai.aiagent.framework.api.ChatRequest;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities;
import io.camunda.connector.agenticai.model.message.UserMessage;
import java.util.List;

/**
 * Routes every {@code Document} found in a {@link ChatRequest} to one of two outcomes, driven by
 * the resolved {@link ModelCapabilities}:
 *
 * <ul>
 *   <li>Tool-result documents are routed against {@link ModelCapabilities#toolResultModalities()}.
 *       Inline-supported modalities are appended to {@code ToolCallResult.contentBlocks} for the
 *       impl to emit natively. Unsupported modalities fall back to a synthetic {@link UserMessage}
 *       carrying the binary, anchored to the originating tool-result message (PR #6999 shape:
 *       header text + per-document XML correlation tag + {@code DocumentContent} block).
 *   <li>User-message and event-message documents are routed against {@link
 *       ModelCapabilities#userMessageModalities()}. Supported modalities stay inline; unsupported
 *       modalities fail loud with {@code ConnectorException} (no synthesis fallback for user
 *       messages, mirroring L4J's {@code DocumentConversionException} semantics).
 * </ul>
 *
 * <p>Implementations must be a pure function over {@code (request, capabilities)}. The returned
 * {@link Result#request()} is the wire form to dispatch (synthetic context messages already
 * interleaved at their anchor positions); {@link Result#syntheticContextMessages()} duplicates
 * those synthetic messages so {@code ChatClientImpl} can persist them into {@code RuntimeMemory} at
 * the matching positions.
 *
 * <p>Part of the ADR-005 Phase E SPI scaffolding.
 */
public interface ToolCallResultStrategy {

  /**
   * Single-pass walk over {@code request.messages()}. Routes every document; rebuilds tool-result
   * messages with populated {@code contentBlocks} where inline; emits synthetic context messages
   * for fallback documents; throws {@code ConnectorException} on user-message capability mismatch.
   */
  Result apply(ChatRequest request, ModelCapabilities capabilities);

  /**
   * @param request rewritten request — tool-result {@code contentBlocks} populated for inline
   *     documents; synthetic context messages interleaved at their anchor positions.
   * @param syntheticContextMessages synthetic {@link UserMessage}s carrying fallback documents
   *     (with {@code METADATA_TOOL_CALL_DOCUMENTS=true}). Duplicates the entries already present in
   *     {@code request.messages()}; surfaced separately so the caller can insert them into {@code
   *     RuntimeMemory} at the matching positions.
   */
  record Result(ChatRequest request, List<UserMessage> syntheticContextMessages) {}
}
