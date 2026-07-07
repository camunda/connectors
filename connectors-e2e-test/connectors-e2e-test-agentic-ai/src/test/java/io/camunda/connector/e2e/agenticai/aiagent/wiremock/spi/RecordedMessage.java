/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A single conversation message recorded from a request sent to a stubbed model endpoint, in a
 * provider-agnostic shape. System/user/assistant/tool-result are the same concepts across all
 * supported wire formats, even though the JSON encoding differs per provider.
 */
public interface RecordedMessage {

  String role();

  /**
   * The message's content parts in wire order (text, images, documents, ...) — excludes tool
   * calls/results, which are carried separately via {@link #toolCalls()}/{@link #toolCallId()}. The
   * source of truth for {@link #textContent()}.
   */
  List<RecordedContentPart> contentParts();

  /**
   * Text content of the message, joined from all {@code "text"} content parts. Regardless of
   * whether the wire format encodes the message as a plain string or a multimodal content array,
   * this ignores non-text parts (e.g. attached images/documents) — use {@link #contentParts()} to
   * inspect those.
   */
  default String textContent() {
    return contentParts().stream()
        .filter(RecordedContentPart::isText)
        .map(RecordedContentPart::text)
        .collect(Collectors.joining());
  }

  List<RecordedToolCall> toolCalls();

  /** Non-null for tool-result messages: the id of the tool call this message answers. */
  String toolCallId();
}
