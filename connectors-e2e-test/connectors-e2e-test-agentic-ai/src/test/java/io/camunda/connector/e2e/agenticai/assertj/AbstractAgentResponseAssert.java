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

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowingConsumer;

public abstract class AbstractAgentResponseAssert<
        SELF extends AbstractAgentResponseAssert<SELF, ACTUAL>, ACTUAL>
    extends AbstractAssert<SELF, ACTUAL> {

  protected AbstractAgentResponseAssert(ACTUAL actual, Class<?> selfType) {
    super(actual, selfType);
  }

  protected abstract AgentContext context(ACTUAL actual);

  protected abstract AssistantMessage responseMessage(ACTUAL actual);

  protected abstract String responseText(ACTUAL actual);

  protected abstract Object responseJson(ACTUAL actual);

  public SELF isReady() {
    return hasState(AgentState.READY);
  }

  public SELF hasState(AgentState expectedState) {
    isNotNull();
    Assertions.assertThat(context(actual).state()).isEqualTo(expectedState);
    return myself;
  }

  public SELF hasMetrics(AgentMetrics expectedMetrics) {
    isNotNull();
    Assertions.assertThat(context(actual).metrics()).isEqualTo(expectedMetrics);
    return myself;
  }

  public SELF hasNoResponseMessage() {
    isNotNull();
    Assertions.assertThat(responseMessage(actual)).isNull();
    return myself;
  }

  public SELF hasResponseMessageSatisfying(ThrowingConsumer<AssistantMessage> assertions) {
    isNotNull();
    Assertions.assertThat(responseMessage(actual)).isNotNull().satisfies(assertions);
    return myself;
  }

  public SELF hasResponseMessageText(String expectedResponseText) {
    isNotNull();
    Assertions.assertThat(responseMessage(actual)).isNotNull();
    Assertions.assertThat(responseMessage(actual).content())
        .hasSize(1)
        .containsExactly(textContent(expectedResponseText));
    return myself;
  }

  public SELF hasNoResponseText() {
    isNotNull();
    Assertions.assertThat(responseText(actual)).isNull();
    return myself;
  }

  public SELF hasResponseText(String expectedResponseText) {
    isNotNull();
    Assertions.assertThat(responseText(actual)).isEqualTo(expectedResponseText);
    return myself;
  }

  public SELF hasResponseTestSatisfying(ThrowingConsumer<String> assertions) {
    isNotNull();
    Assertions.assertThat(responseText(actual)).isNotNull().satisfies(assertions);
    return myself;
  }

  public SELF hasNoResponseJson() {
    isNotNull();
    Assertions.assertThat(responseJson(actual)).isNull();
    return myself;
  }

  public SELF hasResponseJson(Object expectedResponseJson) {
    isNotNull();
    Assertions.assertThat(responseJson(actual)).isNotNull().isEqualTo(expectedResponseJson);
    return myself;
  }

  public SELF hasResponseJsonSatisfying(ThrowingConsumer<Object> assertions) {
    isNotNull();
    Assertions.assertThat(responseJson(actual)).isNotNull().satisfies(assertions);
    return myself;
  }
}
