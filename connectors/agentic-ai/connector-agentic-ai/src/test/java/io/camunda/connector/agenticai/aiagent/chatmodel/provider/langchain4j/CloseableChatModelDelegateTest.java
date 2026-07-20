/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CloseableChatModelDelegateTest {

  @Mock private ChatModel delegate;
  @Mock private AutoCloseable resource;

  private CloseableChatModelDelegate subject;

  @BeforeEach
  void setUp() {
    subject = new CloseableChatModelDelegate(delegate, resource);
  }

  @Test
  void delegatesChatCallToDelegate() {
    final var request = mock(ChatRequest.class);
    final var response = mock(ChatResponse.class);
    when(delegate.chat(request)).thenReturn(response);

    assertThat(subject.chat(request)).isSameAs(response);
    verify(delegate).chat(request);
  }

  @Test
  void delegatesDefaultRequestParameters() {
    final var params = mock(ChatRequestParameters.class);
    when(delegate.defaultRequestParameters()).thenReturn(params);

    assertThat(subject.defaultRequestParameters()).isSameAs(params);
  }

  @Test
  void delegatesListeners() {
    final var listener = mock(ChatModelListener.class);
    when(delegate.listeners()).thenReturn(List.of(listener));

    assertThat(subject.listeners()).containsExactly(listener);
  }

  @Test
  void delegatesProvider() {
    when(delegate.provider()).thenReturn(ModelProvider.AMAZON_BEDROCK);

    assertThat(subject.provider()).isEqualTo(ModelProvider.AMAZON_BEDROCK);
  }

  @Test
  void delegatesSupportedCapabilities() {
    when(delegate.supportedCapabilities())
        .thenReturn(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA));

    assertThat(subject.supportedCapabilities())
        .containsExactly(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
  }

  @Test
  void closesResource() throws Exception {
    subject.close();

    verify(resource).close();
  }

  @Test
  void swallowsExceptionFromResourceClose() throws Exception {
    doThrow(new RuntimeException("close failed")).when(resource).close();

    assertThatNoException().isThrownBy(() -> subject.close());
    verify(resource).close();
  }
}
