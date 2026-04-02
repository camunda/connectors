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

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import org.assertj.core.api.Assertions;

public class AgentResponseAssert
    extends AbstractAgentResponseAssert<AgentResponseAssert, AgentResponse> {

  public AgentResponseAssert(AgentResponse actual) {
    super(actual, AgentResponseAssert.class);
  }

  public static AgentResponseAssert assertThat(AgentResponse actual) {
    return new AgentResponseAssert(actual);
  }

  @Override
  protected AgentContext context(AgentResponse actual) {
    return actual.context();
  }

  @Override
  protected AssistantMessage responseMessage(AgentResponse actual) {
    return actual.responseMessage();
  }

  @Override
  protected String responseText(AgentResponse actual) {
    return actual.responseText();
  }

  @Override
  protected Object responseJson(AgentResponse actual) {
    return actual.responseJson();
  }

  public AgentResponseAssert hasNoToolCalls() {
    isNotNull();
    Assertions.assertThat(actual.toolCalls()).isEmpty();
    return this;
  }
}
