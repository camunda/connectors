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
package io.camunda.connector.runtime.core.inbound.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.Constants;
import io.camunda.connector.impl.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.impl.inbound.result.CorrelationErrorData.CorrelationErrorReason;
import io.camunda.connector.impl.inbound.result.ProcessInstance;
import io.camunda.connector.impl.inbound.result.StartEventCorrelationResult;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.util.command.CreateCommandDummy;
import io.camunda.connector.runtime.core.util.command.PublishMessageCommandDummy;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
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

  @Nested
  class ZeebeClientMethodSelection {

    @Test
    void startEvent_shouldCallCorrectZeebeMethod() {
      // given
      var point = new StartEventCorrelationPoint(0, "process1", 0);

      var dummyCommand = Mockito.spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      // when
      handler.correlate(Map.of(), point, Collections.emptyMap());

      // then
      verify(zeebeClient).newCreateInstanceCommand();
      verifyNoMoreInteractions(zeebeClient);

      verify(dummyCommand).bpmnProcessId(point.getBpmnProcessId());
      verify(dummyCommand).version(point.getVersion());
      verify(dummyCommand).send();
    }

    @Test
    void message_shouldCallCorrectZeebeMethod() {
      // given
      var correlationKeyValue = "someTestCorrelationKeyValue";
      var point = new MessageCorrelationPoint("msg1");
      var properties = Map.of(Constants.CORRELATION_KEY_EXPRESSION_KEYWORD, "=correlationKey");
      Map<String, Object> variables = Map.of("correlationKey", correlationKeyValue);

      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      // when
      handler.correlate(properties, point, variables);

      // then
      verify(zeebeClient).newPublishMessageCommand();
      verifyNoMoreInteractions(zeebeClient);

      verify(dummyCommand).messageName(point.getMessageName());
      verify(dummyCommand).correlationKey(correlationKeyValue);
      verify(dummyCommand).send();
    }
  }

  @Nested
  class ActivationCondition {

    @Test
    void activationConditionFalse_shouldNotCorrelate() {
      // given
      var point = new StartEventCorrelationPoint(0, "process1", 0);
      var properties = Map.of(Constants.ACTIVATION_CONDITION_KEYWORD, "=testKey=\"otherValue\"");

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when
      InboundConnectorResult<?> result = handler.correlate(properties, point, variables);

      // then
      verifyNoMoreInteractions(zeebeClient);

      assertThat(result).isInstanceOf(StartEventCorrelationResult.class);
      assertThat(result.getCorrelationPointId())
          .isEqualTo(String.valueOf(point.getProcessDefinitionKey()));
      assertThat(result.getType()).isEqualTo(StartEventCorrelationResult.TYPE_NAME);
      assertFalse(result.getResponseData().isPresent());
      assertFalse(result.isActivated());
      assertThat(result.getErrorData().isPresent()).isTrue();
      assertThat(result.getErrorData().get().getReason())
          .isEqualTo(CorrelationErrorReason.ACTIVATION_CONDITION_NOT_MET);
    }

    @Test
    void activationConditionTrue_shouldCorrelate() {
      // given
      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      var point = new StartEventCorrelationPoint(0, "process1", 0);
      var properties = Map.of(Constants.ACTIVATION_CONDITION_KEYWORD, "=testKey=\"testValue\"");

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when
      InboundConnectorResult<?> result = handler.correlate(properties, point, variables);

      // then
      verify(zeebeClient).newCreateInstanceCommand();

      assertThat(result).isInstanceOf(StartEventCorrelationResult.class);
      assertThat(result.getCorrelationPointId())
          .isEqualTo(String.valueOf(point.getProcessDefinitionKey()));
      assertThat(result.getType()).isEqualTo(StartEventCorrelationResult.TYPE_NAME);
      assertThat(result.isActivated()).isTrue();
      assertThat(result.getResponseData().isPresent()).isTrue();
      assertThat(result.getErrorData().isPresent()).isFalse();

      ProcessInstance instance = ((StartEventCorrelationResult) result).getResponseData().get();
      assertThat(instance.getProcessDefinitionKey()).isEqualTo(point.getProcessDefinitionKey());
      assertThat(instance.getBpmnProcessId()).isEqualTo(point.getBpmnProcessId());
      assertThat(instance.getVersion()).isEqualTo(point.getVersion());
    }

    @Test
    void activationConditionNull_shouldCorrelate() {
      // given
      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      var point = new StartEventCorrelationPoint(0, "process1", 0);

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when
      InboundConnectorResult<?> result = handler.correlate(Map.of(), point, variables);

      // then
      verify(zeebeClient).newCreateInstanceCommand();

      assertThat(result).isInstanceOf(StartEventCorrelationResult.class);
      assertThat(result.getCorrelationPointId())
          .isEqualTo(String.valueOf(point.getProcessDefinitionKey()));
      assertThat(result.getType()).isEqualTo(StartEventCorrelationResult.TYPE_NAME);
      assertThat(result.isActivated()).isTrue();
      assertThat(result.getResponseData().isPresent()).isTrue();
      assertThat(result.getErrorData().isPresent()).isFalse();

      ProcessInstance instance = ((StartEventCorrelationResult) result).getResponseData().get();
      assertThat(instance.getProcessDefinitionKey()).isEqualTo(point.getProcessDefinitionKey());
      assertThat(instance.getBpmnProcessId()).isEqualTo(point.getBpmnProcessId());
      assertThat(instance.getVersion()).isEqualTo(point.getVersion());
    }

    @Test
    void activationConditionBlank_shouldCorrelate() {
      // given
      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      var point = new StartEventCorrelationPoint(0, "process1", 0);
      var properties = Map.of(Constants.ACTIVATION_CONDITION_KEYWORD, " ");

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when
      InboundConnectorResult<?> result = handler.correlate(properties, point, variables);

      // then
      verify(zeebeClient).newCreateInstanceCommand();

      assertThat(result).isInstanceOf(StartEventCorrelationResult.class);
      assertThat(result.getCorrelationPointId())
          .isEqualTo(String.valueOf(point.getProcessDefinitionKey()));
      assertThat(result.getType()).isEqualTo(StartEventCorrelationResult.TYPE_NAME);
      assertThat(result.isActivated()).isTrue();
      assertThat(result.getResponseData().isPresent()).isTrue();
      assertThat(result.getErrorData().isPresent()).isFalse();

      ProcessInstance instance = ((StartEventCorrelationResult) result).getResponseData().get();
      assertThat(instance.getProcessDefinitionKey()).isEqualTo(point.getProcessDefinitionKey());
      assertThat(instance.getBpmnProcessId()).isEqualTo(point.getBpmnProcessId());
      assertThat(instance.getVersion()).isEqualTo(point.getVersion());
    }
  }

  @Nested
  @SuppressWarnings("unchecked")
  class ResultVariable_And_ResultExpression {

    @Test
    void noResultVar_noResultExpr_shouldNotCopyVariables() {
      // given
      var point = new StartEventCorrelationPoint(0, "process1", 0);

      Map<String, Object> variables = Map.of("testKey", "testValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      // when
      handler.correlate(Map.of(), point, variables);

      // then
      ArgumentCaptor<Map> argumentsCaptured = ArgumentCaptor.forClass(Map.class);
      verify(dummyCommand).variables((Map<String, String>) argumentsCaptured.capture());

      assertThat(argumentsCaptured.getValue()).isEmpty();
    }

    @Test
    void resultVarProvided_noResultExpr_shouldCopyAllVarsToResultVar() {
      // given
      var point = new StartEventCorrelationPoint(0, "process1", 0);
      var properties = Map.of(Constants.RESULT_VARIABLE_KEYWORD, "resultVar");

      Map<String, Object> variables = Map.of("testKey", "testValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      // when
      handler.correlate(properties, point, variables);

      // then
      ArgumentCaptor<Map> argumentsCaptured = ArgumentCaptor.forClass(Map.class);
      verify(dummyCommand).variables((Map<String, String>) argumentsCaptured.capture());

      assertThat(argumentsCaptured.getValue())
          .containsExactlyEntriesOf(Map.of("resultVar", Map.of("testKey", "testValue")));
    }

    @Test
    void noResultVar_resultExprProvided_shouldExtractVariables() {
      // given
      var point = new StartEventCorrelationPoint(0, "process1", 0);
      var properties = Map.of(Constants.RESULT_EXPRESSION_KEYWORD, "={otherKeyAlias: otherKey}");

      Map<String, Object> variables = Map.of("testKey", "testValue", "otherKey", "otherValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      // when
      handler.correlate(properties, point, variables);

      // then
      ArgumentCaptor<Map> argumentsCaptured = ArgumentCaptor.forClass(Map.class);
      verify(dummyCommand).variables((Map<String, String>) argumentsCaptured.capture());

      assertThat(argumentsCaptured.getValue())
          .containsExactlyEntriesOf(Map.of("otherKeyAlias", "otherValue"));
    }

    @Test
    void resultVarProvided_resultExprProvided_shouldExtractVarsAndCopyAllVarsToResultVar() {
      // given
      var point = new StartEventCorrelationPoint(0, "process1", 0);
      var properties = Map.of(
          Constants.RESULT_VARIABLE_KEYWORD, "resultVar",
          Constants.RESULT_EXPRESSION_KEYWORD, "={otherKeyAlias: otherKey}");

      Map<String, Object> variables = Map.of("testKey", "testValue", "otherKey", "otherValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      // when
      handler.correlate(properties, point, variables);

      // then
      ArgumentCaptor<Map> argumentsCaptured = ArgumentCaptor.forClass(Map.class);
      verify(dummyCommand).variables((Map<String, String>) argumentsCaptured.capture());

      assertThat(argumentsCaptured.getValue())
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  "resultVar",
                  Map.of(
                      "otherKey", "otherValue",
                      "testKey", "testValue"),
                  "otherKeyAlias",
                  "otherValue"));
    }
  }
}
