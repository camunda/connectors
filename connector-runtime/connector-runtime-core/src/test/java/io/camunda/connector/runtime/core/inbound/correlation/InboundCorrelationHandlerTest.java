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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.NoOpSecretProvider;
import io.camunda.connector.runtime.core.inbound.DefaultProcessElementContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.BoundaryEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.testutil.command.CreateCommandDummy;
import io.camunda.connector.runtime.core.testutil.command.PublishMessageCommandDummy;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.grpc.Status;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    handler =
        new InboundCorrelationHandler(
            zeebeClient,
            new FeelEngineWrapper(),
            new DefaultProcessElementContextFactory(
                new NoOpSecretProvider(), (e) -> {}, new ObjectMapper()));
  }

  @Test
  void boundaryMessageEvent_shouldCallCorrectZeebeMethod() {
    // given
    var point =
        new BoundaryEventCorrelationPoint(
            "test-boundary",
            "=\"test\"",
            "123",
            new BoundaryEventCorrelationPoint.Activity("123", "test"));
    var element = mock(InboundConnectorElement.class);
    when(element.correlationPoint()).thenReturn(point);

    var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
    when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);

    // when
    handler.correlate(List.of(element), Collections.emptyMap());

    // then
    verify(zeebeClient).newPublishMessageCommand();
    verifyNoMoreInteractions(zeebeClient);

    verify(dummyCommand).messageName("test-boundary");
    verify(dummyCommand).correlationKey("test");
    verify(dummyCommand).messageId("123");
    verify(dummyCommand).send();
  }

  @Test
  void upstreamZeebeError_shouldThrow() {
    // given
    var point =
        new BoundaryEventCorrelationPoint(
            "test-boundary",
            "=\"test\"",
            "123",
            new BoundaryEventCorrelationPoint.Activity("123", "test"));
    var element = mock(InboundConnectorElement.class);
    when(element.correlationPoint()).thenReturn(point);

    when(zeebeClient.newPublishMessageCommand())
        .thenThrow(new ClientStatusException(Status.UNAVAILABLE, null));

    // when & then
    var error =
        assertDoesNotThrow(() -> handler.correlate(List.of(element), Collections.emptyMap()));
    assertThat(error).isInstanceOf(Failure.ZeebeClientStatus.class);
    assertThat(((Failure.ZeebeClientStatus) error).status()).isEqualTo("UNAVAILABLE");
  }

  @Nested
  class ZeebeClientMethodSelection {

    @Test
    void startEvent_shouldCallCorrectZeebeMethod() {
      // given
      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);

      var dummyCommand = Mockito.spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      // when
      var result = handler.correlate(List.of(element), Collections.emptyMap());

      // then
      verify(zeebeClient).newCreateInstanceCommand();
      verifyNoMoreInteractions(zeebeClient);

      verify(dummyCommand).bpmnProcessId(point.bpmnProcessId());
      verify(dummyCommand).version(point.version());
      verify(dummyCommand).send();

      assertThat(result).isInstanceOf(Success.ProcessInstanceCreated.class);
      var success = (Success.ProcessInstanceCreated) result;
      assertThat(success.activatedElement().getElement()).isEqualTo(element.element());
    }

    @Test
    void message_shouldCallCorrectZeebeMethod() {
      // given
      var correlationKeyValue = "someTestCorrelationKeyValue";
      var point = new StandaloneMessageCorrelationPoint("msg1", "=correlationKey", null);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);

      Map<String, Object> variables = Map.of("correlationKey", correlationKeyValue);

      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(zeebeClient).newPublishMessageCommand();
      verifyNoMoreInteractions(zeebeClient);

      verify(dummyCommand).messageName(point.messageName());
      verify(dummyCommand).correlationKey(correlationKeyValue);
      verify(dummyCommand).send();

      assertThat(result).isInstanceOf(Success.MessagePublished.class);
      var success = (Success.MessagePublished) result;
      assertThat(success.activatedElement().getElement()).isEqualTo(element.element());
    }

    @Test
    void startMessageEvent_shouldCallCorrectZeebeMethod() {
      // given
      var point = new MessageStartEventCorrelationPoint("test", "", "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);

      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      // when
      var result = handler.correlate(List.of(element), Collections.emptyMap());

      // then
      verify(zeebeClient).newPublishMessageCommand();
      verifyNoMoreInteractions(zeebeClient);

      verify(dummyCommand).messageName("test");
      verify(dummyCommand).correlationKey("");
      verify(dummyCommand).send();

      assertThat(result).isInstanceOf(Success.MessagePublished.class);
      var success = (Success.MessagePublished) result;
      assertThat(success.activatedElement().getElement()).isEqualTo(element.element());
    }

    @Test
    void startMessageEvent_idempotencyKeyEvaluated() {
      // given
      var point = new MessageStartEventCorrelationPoint("test", "=myVar", "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);

      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      // when
      var result =
          handler.correlate(
              List.of(element),
              Map.of("myVar", "myValue", "myOtherMap", Map.of("myOtherKey", "myOtherValue")));

      // then
      verify(zeebeClient).newPublishMessageCommand();
      verifyNoMoreInteractions(zeebeClient);

      ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
      verify(dummyCommand).messageName("test");
      verify(dummyCommand).correlationKey("");
      verify(dummyCommand).messageId(captor.capture());
      assertThat(captor.getValue()).isEqualTo("myValue");
      verify(dummyCommand).send();

      assertThat(result).isInstanceOf(Success.MessagePublished.class);
      var success = (Success.MessagePublished) result;
      assertThat(success.activatedElement().getElement()).isEqualTo(element.element());
    }

    @Test
    void messageEvent_idempotencyCheckFailed() {
      var point = new MessageStartEventCorrelationPoint("test", "=myVar", "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);

      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(dummyCommand.send())
          .thenThrow(
              new ClientStatusException(
                  Status.fromCode(Status.Code.ALREADY_EXISTS).withDescription("The desc"), null));
      when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      // when
      var result =
          handler.correlate(
              List.of(element),
              Map.of("myVar", "myValue", "myOtherMap", Map.of("myOtherKey", "myOtherValue")));

      // then
      verify(zeebeClient).newPublishMessageCommand();
      verifyNoMoreInteractions(zeebeClient);

      assertThat(result).isInstanceOf(Success.MessageAlreadyCorrelated.class);
      var success = (Success.MessageAlreadyCorrelated) result;
      assertThat(success.activatedElement().getElement()).isEqualTo(element.element());
    }
  }

  @Test
  void multipleElements_singleMatch() {
    // given
    var startEventPoint = new StartEventCorrelationPoint("process1", 0, 0);
    var startEventElement = mock(InboundConnectorElement.class);
    when(startEventElement.correlationPoint()).thenReturn(startEventPoint);
    when(startEventElement.activationCondition()).thenReturn("=testKey=\"testValue1\"");
    var messageElement = mock(InboundConnectorElement.class);
    when(messageElement.activationCondition()).thenReturn("=testKey=\"testValue2\"");

    var dummyCommand = Mockito.spy(new CreateCommandDummy());
    when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

    // when
    var result =
        handler.correlate(
            List.of(startEventElement, messageElement), Map.of("testKey", "testValue1"));

    // then
    verify(zeebeClient).newCreateInstanceCommand();
    verifyNoMoreInteractions(zeebeClient);

    verify(dummyCommand).bpmnProcessId("process1");
    verify(dummyCommand).version(0);
    verify(dummyCommand).send();

    assertThat(result).isInstanceOf(Success.ProcessInstanceCreated.class);
    var success = (Success.ProcessInstanceCreated) result;
    assertThat(success.activatedElement().getElement()).isEqualTo(startEventElement.element());
  }

  @Test
  void multipleElements_multipleMatches_errorRaised() {
    // given
    var startEventElement = mock(InboundConnectorElement.class);
    when(startEventElement.activationCondition()).thenReturn("=testKey=\"testValue\"");
    var messageElement = mock(InboundConnectorElement.class);
    when(messageElement.activationCondition()).thenReturn("=testKey=\"testValue\"");

    // when
    var result =
        handler.correlate(
            List.of(startEventElement, messageElement), Map.of("testKey", "testValue"));

    // then
    assertThat(result).isInstanceOf(Failure.InvalidInput.class);
    assertThat(((Failure.InvalidInput) result).message())
        .contains("Multiple connectors are activated");
  }

  @Nested
  class ActivationCondition {

    @Test
    void activationConditionFalse_shouldNotCorrelate() {
      // given
      var element = mock(InboundConnectorElement.class);
      when(element.activationCondition()).thenReturn("=testKey=\"otherValue\"");

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when & then
      var result = assertDoesNotThrow(() -> handler.correlate(List.of(element), variables));
      verifyNoMoreInteractions(zeebeClient);
      assertThat(result).isInstanceOf(Failure.ActivationConditionNotMet.class);
    }

    @Test
    void activationConditionTrue_shouldCorrelate() {
      // given
      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn("=testKey=\"testValue\"");

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(zeebeClient).newCreateInstanceCommand();
      assertThat(result).isInstanceOf(Success.ProcessInstanceCreated.class);
    }

    @Test
    void activationConditionNull_shouldCorrelate() {
      // given
      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn(null);

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(zeebeClient).newCreateInstanceCommand();
      assertThat(result).isInstanceOf(Success.ProcessInstanceCreated.class);
    }

    @Test
    void activationConditionBlank_shouldCorrelate() {
      // given
      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn("  ");

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(zeebeClient).newCreateInstanceCommand();
      assertThat(result).isInstanceOf(Success.ProcessInstanceCreated.class);
    }

    @Test
    void messageStartEvent_activationConditionTrue_shouldCorrelate() {
      // given
      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      var point = new MessageStartEventCorrelationPoint("testMsg", "=myVar", "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn("=myOtherMap.myOtherKey=\"myOtherValue\"");

      Map<String, Object> variables =
          Map.of("myVar", "myValue", "myOtherMap", Map.of("myOtherKey", "myOtherValue"));

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(zeebeClient).newPublishMessageCommand();
      assertThat(result).isInstanceOf(Success.MessagePublished.class);
    }

    @Test
    void messageStartEvent_activationConditionNull_shouldCorrelate() {
      // given
      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      var point = new MessageStartEventCorrelationPoint("testMsg", "=myVar", "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn(null);

      Map<String, Object> variables =
          Map.of("myVar", "myValue", "myOtherMap", Map.of("myOtherKey", "myOtherValue"));

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(zeebeClient).newPublishMessageCommand();
      assertThat(result).isInstanceOf(Success.MessagePublished.class);
    }

    @Test
    void messageStartEvent_activationConditionBlank_shouldCorrelate() {
      // given
      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      var point = new MessageStartEventCorrelationPoint("testMsg", "=myVar", "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn("  ");

      Map<String, Object> variables =
          Map.of("myVar", "myValue", "myOtherMap", Map.of("myOtherKey", "myOtherValue"));

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(zeebeClient).newPublishMessageCommand();
      assertThat(result).isInstanceOf(Success.MessagePublished.class);
    }
  }

  @Nested
  @SuppressWarnings("unchecked")
  class ResultVariable_And_ResultExpression {

    @Test
    void noResultVar_noResultExpr_shouldNotCopyVariables() {
      // given
      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);

      Map<String, Object> variables = Map.of("testKey", "testValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      // when
      handler.correlate(List.of(element), variables);

      // then
      var argumentsCaptured = ArgumentCaptor.forClass(Map.class);
      verify(dummyCommand).variables((Map<String, String>) argumentsCaptured.capture());

      assertThat(argumentsCaptured.getValue()).isEmpty();
    }

    @Test
    void resultVarProvided_noResultExpr_shouldCopyAllVarsToResultVar() {
      // given
      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.resultVariable()).thenReturn("resultVar");

      Map<String, Object> variables = Map.of("testKey", "testValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      // when
      handler.correlate(List.of(element), variables);

      // then
      var argumentsCaptured = ArgumentCaptor.forClass(Map.class);
      verify(dummyCommand).variables((Map<String, String>) argumentsCaptured.capture());

      assertThat(argumentsCaptured.getValue())
          .containsExactlyEntriesOf(Map.of("resultVar", Map.of("testKey", "testValue")));
    }

    @Test
    void noResultVar_resultExprProvided_shouldExtractVariables() {
      // given
      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.resultExpression()).thenReturn("={otherKeyAlias: otherKey}");

      Map<String, Object> variables = Map.of("testKey", "testValue", "otherKey", "otherValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      // when
      handler.correlate(List.of(element), variables);

      // then
      var argumentsCaptured = ArgumentCaptor.forClass(Map.class);
      verify(dummyCommand).variables((Map<String, String>) argumentsCaptured.capture());

      assertThat(argumentsCaptured.getValue())
          .containsExactlyEntriesOf(Map.of("otherKeyAlias", "otherValue"));
    }

    @Test
    void resultVarProvided_resultExprProvided_shouldExtractVarsAndCopyAllVarsToResultVar() {
      // given
      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.resultVariable()).thenReturn("resultVar");
      when(element.resultExpression()).thenReturn("={otherKeyAlias: otherKey}");

      Map<String, Object> variables = Map.of("testKey", "testValue", "otherKey", "otherValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(zeebeClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      // when
      handler.correlate(List.of(element), variables);

      // then
      var argumentsCaptured = ArgumentCaptor.forClass(Map.class);
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

  @Nested
  class ResolveMessageId {

    @Test
    void messageIdIsNull_expressionIsNull_usesRandomUuid() {
      // given
      var point = new StandaloneMessageCorrelationPoint("msg1", "=correlationKey", null);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);

      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);
      // when
      handler.correlate(List.of(element), Collections.singletonMap("correlationKey", "testkey"));
      // then
      ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
      verify(dummyCommand).messageId(messageIdCaptor.capture());

      String resolvedMessageId = messageIdCaptor.getValue();
      assertThat(UUID.fromString(resolvedMessageId))
          .isNotNull(); // If this doesn't throw an exception, it's a UUID.
    }

    @Test
    void messageIdIsNull_expressionIsProvided_usesExtractedMessageId() {
      // given
      var point = new StandaloneMessageCorrelationPoint("msg1", "=extractedId", "=extractedId");
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);
      Map<String, Object> variables = Map.of("extractedId", "resolvedIdValue");
      // when
      handler.correlate(List.of(element), variables);
      // then
      verify(dummyCommand).messageId("resolvedIdValue");
    }

    @Test
    void messageIdIsProvided_usesGivenMessageId() {
      // given
      var point = new StandaloneMessageCorrelationPoint("msg1", "=123", null);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(zeebeClient.newPublishMessageCommand()).thenReturn(dummyCommand);
      // when
      handler.correlate(List.of(element), Collections.emptyMap(), "providedIdValue");
      // then
      verify(dummyCommand).messageId("providedIdValue");
    }
  }
}
