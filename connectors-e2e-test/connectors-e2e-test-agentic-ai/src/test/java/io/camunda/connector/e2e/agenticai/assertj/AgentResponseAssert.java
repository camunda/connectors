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

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowingConsumer;

public class AgentResponseAssert extends AbstractAssert<AgentResponseAssert, AgentResponse> {
  public AgentResponseAssert(AgentResponse actual) {
    super(actual, AgentResponseAssert.class);
  }

  public static AgentResponseAssert assertThat(AgentResponse actual) {
    return new AgentResponseAssert(actual);
  }

  public AgentResponseAssert isReady() {
    return hasState(AgentState.READY);
  }

  public AgentResponseAssert hasState(AgentState expectedState) {
    isNotNull();
    Assertions.assertThat(actual.context().state()).isEqualTo(expectedState);
    return this;
  }

  public AgentResponseAssert hasNoToolCalls() {
    isNotNull();
    Assertions.assertThat(actual.toolCalls()).isEmpty();
    return this;
  }

  public AgentResponseAssert hasMetrics(AgentMetrics expectedMetrics) {
    isNotNull();
    Assertions.assertThat(actual.context().metrics()).isEqualTo(expectedMetrics);
    return this;
  }

  public AgentResponseAssert hasNoResponseMessage() {
    isNotNull();
    Assertions.assertThat(actual.responseMessage()).isNull();
    return this;
  }

  public AgentResponseAssert hasResponseMessageSatisfying(
      ThrowingConsumer<AssistantMessage> assertions) {
    isNotNull();
    Assertions.assertThat(actual.responseMessage()).isNotNull().satisfies(assertions);
    return this;
  }

  public AgentResponseAssert hasResponseMessageText(String expectedResponseText) {
    isNotNull();
    Assertions.assertThat(actual.responseMessage()).isNotNull();
    Assertions.assertThat(actual.responseMessage().content())
        .hasSize(1)
        .containsExactly(textContent(expectedResponseText));
    return this;
  }

  public AgentResponseAssert hasNoResponseText() {
    isNotNull();
    Assertions.assertThat(actual.responseText()).isNull();
    return this;
  }

  public AgentResponseAssert hasResponseText(String expectedResponseText) {
    isNotNull();
    Assertions.assertThat(actual.responseText()).isEqualTo(expectedResponseText);
    return this;
  }

  public AgentResponseAssert hasResponseTestSatisfying(ThrowingConsumer<String> assertions) {
    isNotNull();
    Assertions.assertThat(actual.responseText()).isNotNull().satisfies(assertions);
    return this;
  }

  public AgentResponseAssert hasNoResponseJson() {
    isNotNull();
    Assertions.assertThat(actual.responseJson()).isNull();
    return this;
  }

  public AgentResponseAssert hasResponseJson(Object expectedResponseJson) {
    isNotNull();
    Assertions.assertThat(actual.responseJson()).isNotNull().isEqualTo(expectedResponseJson);
    return this;
  }

  public AgentResponseAssert hasResponseJsonSatisfying(ThrowingConsumer<Object> assertions) {
    isNotNull();
    Assertions.assertThat(actual.responseJson()).isNotNull().satisfies(assertions);
    return this;
  }
}
