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

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessResponse;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowingConsumer;

public class AgentSubProcessResponseAssert
    extends AbstractAssert<AgentSubProcessResponseAssert, AgentSubProcessResponse> {
  public AgentSubProcessResponseAssert(AgentSubProcessResponse actual) {
    super(actual, AgentSubProcessResponseAssert.class);
  }

  public static AgentSubProcessResponseAssert assertThat(AgentSubProcessResponse actual) {
    return new AgentSubProcessResponseAssert(actual);
  }

  public AgentSubProcessResponseAssert isReady() {
    return hasState(AgentState.READY);
  }

  public AgentSubProcessResponseAssert hasState(AgentState expectedState) {
    isNotNull();
    Assertions.assertThat(actual.context().state()).isEqualTo(expectedState);
    return this;
  }

  public AgentSubProcessResponseAssert hasMetrics(AgentMetrics expectedMetrics) {
    isNotNull();
    Assertions.assertThat(actual.context().metrics()).isEqualTo(expectedMetrics);
    return this;
  }

  public AgentSubProcessResponseAssert hasNoResponseMessage() {
    isNotNull();
    Assertions.assertThat(actual.responseMessage()).isNull();
    return this;
  }

  public AgentSubProcessResponseAssert hasResponseMessageSatisfying(
      ThrowingConsumer<AssistantMessage> assertions) {
    isNotNull();
    Assertions.assertThat(actual.responseMessage()).isNotNull().satisfies(assertions);
    return this;
  }

  public AgentSubProcessResponseAssert hasResponseMessageText(String expectedResponseText) {
    isNotNull();
    Assertions.assertThat(actual.responseMessage()).isNotNull();
    Assertions.assertThat(actual.responseMessage().content())
        .hasSize(1)
        .containsExactly(textContent(expectedResponseText));
    return this;
  }

  public AgentSubProcessResponseAssert hasNoResponseText() {
    isNotNull();
    Assertions.assertThat(actual.responseText()).isNull();
    return this;
  }

  public AgentSubProcessResponseAssert hasResponseText(String expectedResponseText) {
    isNotNull();
    Assertions.assertThat(actual.responseText()).isEqualTo(expectedResponseText);
    return this;
  }

  public AgentSubProcessResponseAssert hasResponseTestSatisfying(
      ThrowingConsumer<String> assertions) {
    isNotNull();
    Assertions.assertThat(actual.responseText()).isNotNull().satisfies(assertions);
    return this;
  }

  public AgentSubProcessResponseAssert hasNoResponseJson() {
    isNotNull();
    Assertions.assertThat(actual.responseJson()).isNull();
    return this;
  }

  public AgentSubProcessResponseAssert hasResponseJson(Object expectedResponseJson) {
    isNotNull();
    Assertions.assertThat(actual.responseJson()).isNotNull().isEqualTo(expectedResponseJson);
    return this;
  }

  public AgentSubProcessResponseAssert hasResponseJsonSatisfying(
      ThrowingConsumer<Object> assertions) {
    isNotNull();
    Assertions.assertThat(actual.responseJson()).isNotNull().satisfies(assertions);
    return this;
  }

  public AgentSubProcessResponseAssert hasAgentInstanceKey() {
    isNotNull();
    Assertions.assertThat(actual.context()).isNotNull();
    Assertions.assertThat(actual.context().metadata()).isNotNull();
    Assertions.assertThat(actual.context().metadata().agentInstanceKey()).isNotNull().isPositive();
    return this;
  }

  public AgentSubProcessResponseAssert hasLastIterationKey(int expectedLastIterationKey) {
    isNotNull();
    Assertions.assertThat(actual.context()).isNotNull();
    Assertions.assertThat(actual.context().metadata()).isNotNull();
    Assertions.assertThat(actual.context().metadata().lastIterationKey())
        .isEqualTo(expectedLastIterationKey);
    return this;
  }
}
