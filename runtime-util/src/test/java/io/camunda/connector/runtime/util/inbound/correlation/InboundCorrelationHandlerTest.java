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
package io.camunda.connector.runtime.util.inbound.correlation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.impl.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.util.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.util.util.command.CreateCommandDummy;
import io.camunda.connector.runtime.util.util.command.PublishMessageCommandDummy;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InboundCorrelationHandlerTest {

  private ZeebeClient zeebeClient;
  private InboundCorrelationHandler handler;

  @BeforeEach
  public void initMock() {
    zeebeClient = mock(ZeebeClient.class);
    handler = new InboundCorrelationHandler(zeebeClient, new FeelEngineWrapper());
  }

  @Test
  public void startEvent_shouldCallCorrectZeebeMethod() {
    // given
    var point = new StartEventCorrelationPoint(0, "process1", 0);
    Map<String, Object> variables = Map.of("testKey", "testValue");

    var dummyCommand = spy(new CreateCommandDummy());
    when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

    // when
    handler.correlate(point, variables);

    // then
    verify(zeebeClient).newCreateInstanceCommand();
    verifyNoMoreInteractions(zeebeClient);

    verify(dummyCommand).bpmnProcessId(point.getBpmnProcessId());
    verify(dummyCommand).version(point.getVersion());
    verify(dummyCommand).variables((Object) variables);
    verify(dummyCommand).send();
  }

  @Test
  public void message_shouldCallCorrectZeebeMethod() {
    // given
    var correlationKeyValue = "someTestCorrelationKeyValue";
    var point = new MessageCorrelationPoint("msg1", "=correlationKey");
    Map<String, Object> variables = Map.of("correlationKey", correlationKeyValue);

    var dummyCommand = spy(new PublishMessageCommandDummy());
    when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);

    // when
    handler.correlate(point, variables);

    // then
    verify(zeebeClient).newPublishMessageCommand();
    verifyNoMoreInteractions(zeebeClient);

    verify(dummyCommand).messageName(point.getMessageName());
    verify(dummyCommand).correlationKey(correlationKeyValue);
    verify(dummyCommand).variables((Object) variables);
    verify(dummyCommand).send();
  }
}
