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
package io.camunda.connector.e2e.agenticai.assertj;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentResponse;
import org.junit.jupiter.api.Test;

class JobWorkerAgentResponseAssertTest {

  @Test
  void reasoningTokensGreaterThanZeroPassesWhenPositiveAndFailsWhenZero() {
    final var response = mock(JobWorkerAgentResponse.class, RETURNS_DEEP_STUBS);

    when(response.context().metrics().tokenUsage().reasoningTokenCount()).thenReturn(7);
    assertThatCode(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasReasoningTokensGreaterThanZero())
        .doesNotThrowAnyException();

    when(response.context().metrics().tokenUsage().reasoningTokenCount()).thenReturn(0);
    assertThatThrownBy(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasReasoningTokensGreaterThanZero())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void cacheCreationTokensGreaterThanZeroPassesWhenPositiveAndFailsWhenZero() {
    final var response = mock(JobWorkerAgentResponse.class, RETURNS_DEEP_STUBS);

    when(response.context().metrics().tokenUsage().cacheCreationTokenCount()).thenReturn(1024);
    assertThatCode(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasCacheCreationTokensGreaterThanZero())
        .doesNotThrowAnyException();

    when(response.context().metrics().tokenUsage().cacheCreationTokenCount()).thenReturn(0);
    assertThatThrownBy(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasCacheCreationTokensGreaterThanZero())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void cacheReadTokensGreaterThanZeroPassesWhenPositiveAndFailsWhenZero() {
    final var response = mock(JobWorkerAgentResponse.class, RETURNS_DEEP_STUBS);

    when(response.context().metrics().tokenUsage().cacheReadTokenCount()).thenReturn(512);
    assertThatCode(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasCacheReadTokensGreaterThanZero())
        .doesNotThrowAnyException();

    when(response.context().metrics().tokenUsage().cacheReadTokenCount()).thenReturn(0);
    assertThatThrownBy(
            () ->
                JobWorkerAgentResponseAssert.assertThat(response)
                    .hasCacheReadTokensGreaterThanZero())
        .isInstanceOf(AssertionError.class);
  }
}
