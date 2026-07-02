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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.azureopenai;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedMessage;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedResponseFormat;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedToolCall;
import java.util.List;
import java.util.Optional;

/**
 * Adapts {@link AzureOpenAiCompletionsRecordedConversation.RecordedChatRequest} to the SPI shape.
 */
final class AzureOpenAiCompletionsRecordedChatRequestAdapter implements RecordedChatRequest {

  private final AzureOpenAiCompletionsRecordedConversation.RecordedChatRequest delegate;

  AzureOpenAiCompletionsRecordedChatRequestAdapter(
      AzureOpenAiCompletionsRecordedConversation.RecordedChatRequest delegate) {
    this.delegate = delegate;
  }

  @Override
  public List<RecordedMessage> messages() {
    return delegate.messages().stream().<RecordedMessage>map(MessageAdapter::new).toList();
  }

  @Override
  public List<ToolDefinition> toolDefinitions() {
    return delegate.toolDefinitions();
  }

  @Override
  public Optional<RecordedResponseFormat> responseFormat() {
    return delegate
        .responseFormat()
        .map(format -> new RecordedResponseFormat(format.type(), format.jsonSchema()));
  }

  private record MessageAdapter(AzureOpenAiCompletionsRecordedConversation.RecordedMessage delegate)
      implements RecordedMessage {

    @Override
    public String role() {
      return delegate.role();
    }

    @Override
    public String textContent() {
      return delegate.textContent();
    }

    @Override
    public List<RecordedToolCall> toolCalls() {
      return delegate.toolCalls().stream().<RecordedToolCall>map(ToolCallAdapter::new).toList();
    }

    @Override
    public String toolCallId() {
      return delegate.toolCallId();
    }
  }

  private record ToolCallAdapter(
      AzureOpenAiCompletionsRecordedConversation.RecordedMessage.RecordedToolCall delegate)
      implements RecordedToolCall {

    @Override
    public String id() {
      return delegate.id();
    }

    @Override
    public String name() {
      return delegate.name();
    }
  }
}
