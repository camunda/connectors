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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SlowTest
class L4JAiAgentJobWorkerAgentInstanceTests extends BaseL4JAiAgentJobWorkerTest {

  @MockitoSpyBean private AgentInstanceClient camundaAgentInstanceClient;

  @Test
  void executesAgentWithoutUserFeedback() throws Exception {
    testBasicExecutionWithoutFeedbackLoop(
        e -> e,
        HAIKU_TEXT,
        true,
        (agentResponse) -> {
          JobWorkerAgentResponseAssert.assertThat(agentResponse).hasAgentInstanceKey();
          verify(camundaAgentInstanceClient, times(1)).create(any());
        });
  }
}
