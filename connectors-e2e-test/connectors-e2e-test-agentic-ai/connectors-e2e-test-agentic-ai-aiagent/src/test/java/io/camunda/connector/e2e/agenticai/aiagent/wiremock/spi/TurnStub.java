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

/**
 * A single assistant turn to stub, in a provider-agnostic shape. Mirrors the shape of the
 * conversation the AI Agent connector drives against the stubbed model endpoint, independent of how
 * a specific provider/API encodes it on the wire.
 */
public sealed interface TurnStub {

  static TurnStub text(String text, int inputTokens, int outputTokens) {
    return new Text(text, inputTokens, outputTokens);
  }

  static TurnStub toolCalls(
      String text, int inputTokens, int outputTokens, ToolCallStub... toolCalls) {
    return new ToolCalls(text, inputTokens, outputTokens, List.of(toolCalls));
  }

  /** A plain text response that ends the turn. */
  record Text(String text, int inputTokens, int outputTokens) implements TurnStub {}

  /**
   * A tool-call response. The optional assistant text is included alongside the tool calls,
   * matching how providers return reasoning text with tool calls.
   */
  record ToolCalls(String text, int inputTokens, int outputTokens, List<ToolCallStub> toolCalls)
      implements TurnStub {}
}
