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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.NoOpSecretProvider;
import io.camunda.connector.runtime.core.TestObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.DefaultProcessElementContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.BoundaryEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.testutil.command.CreateCommandDummy;
import io.camunda.connector.runtime.core.testutil.command.PublishMessageCommandDummy;
import io.grpc.Status;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InboundCorrelationHandlerTest {

  private static final Duration DEFAULT_TTL = Duration.ofHours(2);
  private CamundaClient camundaClient;
  private InboundCorrelationHandler handler;

  public static Stream<Arguments> durationsProvider() {
    return Stream.of(Arguments.of(Duration.ofSeconds(10)), null);
  }

  @BeforeEach
  public void initMock() {
    camundaClient = mock(CamundaClient.class);
    handler =
        new InboundCorrelationHandler(
            camundaClient,
            new FeelEngineWrapper(),
            TestObjectMapperSupplier.INSTANCE,
            new DefaultProcessElementContextFactory(
                new NoOpSecretProvider(), (e) -> {}, TestObjectMapperSupplier.INSTANCE),
            DEFAULT_TTL);
  }

  @ParameterizedTest
  @MethodSource("durationsProvider")
  void boundaryMessageEvent_shouldCallCorrectZeebeMethod(Duration duration) {
    // given
    var point =
        new BoundaryEventCorrelationPoint(
            "test-boundary",
            "=\"test\"",
            "123",
            duration,
            new BoundaryEventCorrelationPoint.Activity("123", "test"));
    var element = mock(InboundConnectorElement.class);
    when(element.correlationPoint()).thenReturn(point);
    when(element.element()).thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

    var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
    when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);

    // when
    handler.correlate(List.of(element), Collections.emptyMap());

    // then
    verify(camundaClient).newPublishMessageCommand();
    verifyNoMoreInteractions(camundaClient);

    verify(dummyCommand).messageName("test-boundary");
    verify(dummyCommand).correlationKey("test");
    verify(dummyCommand).messageId("123");
    verify(dummyCommand).timeToLive(Optional.ofNullable(duration).orElse(DEFAULT_TTL));
    verify(dummyCommand).send();
  }

  @ParameterizedTest
  @MethodSource("durationsProvider")
  void upstreamZeebeError_shouldThrow(Duration duration) {
    // given
    var point =
        new BoundaryEventCorrelationPoint(
            "test-boundary",
            "=\"test\"",
            "123",
            duration,
            new BoundaryEventCorrelationPoint.Activity("123", "test"));
    var element = mock(InboundConnectorElement.class);
    when(element.correlationPoint()).thenReturn(point);
    when(element.element()).thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

    when(camundaClient.newPublishMessageCommand())
        .thenThrow(new ClientStatusException(Status.UNAVAILABLE, null));

    // when & then
    var error =
        assertDoesNotThrow(() -> handler.correlate(List.of(element), Collections.emptyMap()));
    assertThat(error).isInstanceOf(Failure.ZeebeClientStatus.class);
    assertThat(((Failure.ZeebeClientStatus) error).status()).isEqualTo("UNAVAILABLE");
  }

  @Test
  void multipleElements_singleMatch() {
    // given
    var startEventPoint = new StartEventCorrelationPoint("process1", 0, 0);
    var startEventElement = mock(InboundConnectorElement.class);
    when(startEventElement.correlationPoint()).thenReturn(startEventPoint);
    when(startEventElement.element())
        .thenReturn(new ProcessElement("process1", 0, 0, "startEventElementId", "default"));
    when(startEventElement.activationCondition()).thenReturn("=testKey=\"testValue1\"");
    var messageElement = mock(InboundConnectorElement.class);
    when(messageElement.activationCondition()).thenReturn("=testKey=\"testValue2\"");

    var dummyCommand = Mockito.spy(new CreateCommandDummy());
    when(camundaClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

    // when
    var result =
        handler.correlate(
            List.of(startEventElement, messageElement), Map.of("testKey", "testValue1"));

    // then
    verify(camundaClient).newCreateInstanceCommand();
    verifyNoMoreInteractions(camundaClient);

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
  class ZeebeClientMethodSelection {

    @Test
    void startEvent_shouldCallCorrectZeebeMethod() {
      // given
      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      var dummyCommand = Mockito.spy(new CreateCommandDummy());
      when(camundaClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      // when
      var result = handler.correlate(List.of(element), Collections.emptyMap());

      // then
      verify(camundaClient).newCreateInstanceCommand();
      verifyNoMoreInteractions(camundaClient);

      verify(dummyCommand).bpmnProcessId(point.bpmnProcessId());
      verify(dummyCommand).version(point.version());
      verify(dummyCommand).send();

      assertThat(result).isInstanceOf(Success.ProcessInstanceCreated.class);
      var success = (Success.ProcessInstanceCreated) result;
      assertThat(success.activatedElement().getElement()).isEqualTo(element.element());
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandlerTest#durationsProvider")
    void message_shouldCallCorrectZeebeMethod(Duration duration) {
      // given
      var correlationKeyValue = "someTestCorrelationKeyValue";
      var point = new StandaloneMessageCorrelationPoint("msg1", "=correlationKey", null, duration);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      Map<String, Object> variables = Map.of("correlationKey", correlationKeyValue);

      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(camundaClient).newPublishMessageCommand();
      verifyNoMoreInteractions(camundaClient);

      verify(dummyCommand).messageName(point.messageName());
      verify(dummyCommand).correlationKey(correlationKeyValue);
      verify(dummyCommand).timeToLive(Optional.ofNullable(duration).orElse(DEFAULT_TTL));
      verify(dummyCommand).send();

      assertThat(result).isInstanceOf(Success.MessagePublished.class);
      var success = (Success.MessagePublished) result;
      assertThat(success.activatedElement().getElement()).isEqualTo(element.element());
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandlerTest#durationsProvider")
    void startMessageEvent_shouldCallCorrectZeebeMethod(Duration duration) {
      // given
      var point = new MessageStartEventCorrelationPoint("test", "", duration, "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      // when
      var result = handler.correlate(List.of(element), Collections.emptyMap());

      // then
      verify(camundaClient).newPublishMessageCommand();
      verifyNoMoreInteractions(camundaClient);

      verify(dummyCommand).messageName("test");
      verify(dummyCommand).correlationKey("");
      verify(dummyCommand).timeToLive(Optional.ofNullable(duration).orElse(DEFAULT_TTL));
      verify(dummyCommand).send();

      assertThat(result).isInstanceOf(Success.MessagePublished.class);
      var success = (Success.MessagePublished) result;
      assertThat(success.activatedElement().getElement()).isEqualTo(element.element());
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandlerTest#durationsProvider")
    void startMessageEvent_idempotencyKeyEvaluated(Duration duration) {
      // given
      var point = new MessageStartEventCorrelationPoint("test", "=myVar", duration, "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      // when
      var result =
          handler.correlate(
              List.of(element),
              Map.of("myVar", "myValue", "myOtherMap", Map.of("myOtherKey", "myOtherValue")));

      // then
      verify(camundaClient).newPublishMessageCommand();
      verifyNoMoreInteractions(camundaClient);

      ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
      verify(dummyCommand).messageName("test");
      verify(dummyCommand).correlationKey("");
      verify(dummyCommand).messageId(captor.capture());
      verify(dummyCommand).timeToLive(Optional.ofNullable(duration).orElse(DEFAULT_TTL));
      assertThat(captor.getValue()).isEqualTo("myValue");
      verify(dummyCommand).send();

      assertThat(result).isInstanceOf(Success.MessagePublished.class);
      var success = (Success.MessagePublished) result;
      assertThat(success.activatedElement().getElement()).isEqualTo(element.element());
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandlerTest#durationsProvider")
    void messageEvent_idempotencyCheckFailed(Duration duration) {
      var point = new MessageStartEventCorrelationPoint("test", "=myVar", duration, "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(dummyCommand.send())
          .thenThrow(
              new ClientStatusException(
                  Status.fromCode(Status.Code.ALREADY_EXISTS).withDescription("The desc"), null));
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      // when
      var result =
          handler.correlate(
              List.of(element),
              Map.of("myVar", "myValue", "myOtherMap", Map.of("myOtherKey", "myOtherValue")));

      // then
      verify(dummyCommand).timeToLive(Optional.ofNullable(duration).orElse(DEFAULT_TTL));
      verify(camundaClient).newPublishMessageCommand();
      verifyNoMoreInteractions(camundaClient);

      assertThat(result).isInstanceOf(Success.MessageAlreadyCorrelated.class);
      var success = (Success.MessageAlreadyCorrelated) result;
      assertThat(success.activatedElement().getElement()).isEqualTo(element.element());
    }
  }

  @Nested
  class ActivationCondition {

    @Test
    void activationConditionFalse_strategyForwardErrorToUpstream() {
      // given
      var element = mock(InboundConnectorElement.class);
      when(element.activationCondition()).thenReturn("=testKey=\"otherValue\"");
      when(element.consumeUnmatchedEvents()).thenReturn(false);

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when & then
      var result = assertDoesNotThrow(() -> handler.correlate(List.of(element), variables));
      verifyNoMoreInteractions(camundaClient);
      assertThat(result).isInstanceOf(Failure.ActivationConditionNotMet.class);
      assertThat(((Failure.ActivationConditionNotMet) result).handlingStrategy())
          .isInstanceOf(CorrelationFailureHandlingStrategy.ForwardErrorToUpstream.class);
    }

    @Test
    void activationConditionFalse_strategyIgnore() {
      // given
      var element = mock(InboundConnectorElement.class);
      when(element.activationCondition()).thenReturn("=testKey=\"otherValue\"");
      when(element.consumeUnmatchedEvents()).thenReturn(true);

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when & then
      var result = assertDoesNotThrow(() -> handler.correlate(List.of(element), variables));
      verifyNoMoreInteractions(camundaClient);
      assertThat(result).isInstanceOf(Failure.ActivationConditionNotMet.class);
      assertThat(((Failure) result).handlingStrategy())
          .isInstanceOf(CorrelationFailureHandlingStrategy.Ignore.class);
    }

    @Test
    void activationConditionTrue_shouldCorrelate() {
      // given
      var dummyCommand = spy(new CreateCommandDummy());
      when(camundaClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn("=testKey=\"testValue\"");
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(camundaClient).newCreateInstanceCommand();
      assertThat(result).isInstanceOf(Success.ProcessInstanceCreated.class);
    }

    @Test
    void activationConditionNull_shouldCorrelate() {
      // given
      var dummyCommand = spy(new CreateCommandDummy());
      when(camundaClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn(null);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(camundaClient).newCreateInstanceCommand();
      assertThat(result).isInstanceOf(Success.ProcessInstanceCreated.class);
    }

    @Test
    void activationConditionBlank_shouldCorrelate() {
      // given
      var dummyCommand = spy(new CreateCommandDummy());
      when(camundaClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

      var point = new StartEventCorrelationPoint("process1", 0, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn("  ");
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      Map<String, Object> variables = Map.of("testKey", "testValue");

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(camundaClient).newCreateInstanceCommand();
      assertThat(result).isInstanceOf(Success.ProcessInstanceCreated.class);
    }

    @Test
    void messageStartEvent_activationConditionTrue_shouldCorrelate() {
      // given
      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      var point = new MessageStartEventCorrelationPoint("testMsg", "=myVar", null, "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn("=myOtherMap.myOtherKey=\"myOtherValue\"");
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      Map<String, Object> variables =
          Map.of("myVar", "myValue", "myOtherMap", Map.of("myOtherKey", "myOtherValue"));

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(camundaClient).newPublishMessageCommand();
      assertThat(result).isInstanceOf(Success.MessagePublished.class);
    }

    @Test
    void messageStartEvent_activationConditionNull_shouldCorrelate() {
      // given
      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      var point = new MessageStartEventCorrelationPoint("testMsg", "=myVar", null, "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn(null);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      Map<String, Object> variables =
          Map.of("myVar", "myValue", "myOtherMap", Map.of("myOtherKey", "myOtherValue"));

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(camundaClient).newPublishMessageCommand();
      assertThat(result).isInstanceOf(Success.MessagePublished.class);
    }

    @Test
    void messageStartEvent_activationConditionBlank_shouldCorrelate() {
      // given
      var dummyCommand = Mockito.spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);

      var point = new MessageStartEventCorrelationPoint("testMsg", "=myVar", null, "", "1", 1, 0);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.activationCondition()).thenReturn("  ");
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      Map<String, Object> variables =
          Map.of("myVar", "myValue", "myOtherMap", Map.of("myOtherKey", "myOtherValue"));

      // when
      var result = handler.correlate(List.of(element), variables);

      // then
      verify(camundaClient).newPublishMessageCommand();
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
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      Map<String, Object> variables = Map.of("testKey", "testValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(camundaClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

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
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      Map<String, Object> variables = Map.of("testKey", "testValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(camundaClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

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
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      Map<String, Object> variables = Map.of("testKey", "testValue", "otherKey", "otherValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(camundaClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

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
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      Map<String, Object> variables = Map.of("testKey", "testValue", "otherKey", "otherValue");

      var dummyCommand = spy(new CreateCommandDummy());
      when(camundaClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

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
      var point = new StandaloneMessageCorrelationPoint("msg1", "=correlationKey", null, null);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);
      // when
      handler.correlate(List.of(element), Collections.singletonMap("correlationKey", "testkey"));
      // then
      ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
      verify(dummyCommand).messageId(messageIdCaptor.capture());

      String resolvedMessageId = messageIdCaptor.getValue();
      assertThat(resolvedMessageId).isNotNull(); // If this doesn't throw an exception, it's a UUID.
    }

    @Test
    void messageIdIsNull_expressionIsProvided_usesExtractedMessageId() {
      // given
      var point =
          new StandaloneMessageCorrelationPoint("msg1", "=extractedId", "=extractedId", null);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);
      Map<String, Object> variables = Map.of("extractedId", "resolvedIdValue");
      // when
      handler.correlate(List.of(element), variables);
      // then
      verify(dummyCommand).messageId("resolvedIdValue");
    }

    @Test
    void messageIdIsProvided_usesGivenMessageId() {
      // given
      var point = new StandaloneMessageCorrelationPoint("msg1", "=123", null, null);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);
      // when
      handler.correlate(
          List.of(element),
          CorrelationRequest.builder()
              .variables(Collections.emptyMap())
              .messageId("providedIdValue")
              .build());
      // then
      verify(dummyCommand).messageId("providedIdValue");
    }

    @Test
    void messageIdIsProvided_messageIdExpressionIsUsedInPriority() {
      // given
      var point = new StandaloneMessageCorrelationPoint("msg1", "=123", "=456", null);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);
      // when
      handler.correlate(
          List.of(element),
          CorrelationRequest.builder()
              .variables(Collections.emptyMap())
              .messageId("providedIdValue")
              .build());
      // then
      verify(dummyCommand).messageId("456");
    }

    @Test
    void messageIdIsProvided_messageIdExpressionIsUsed() {
      // given
      var point = new StandaloneMessageCorrelationPoint("msg1", "=123", "=456", null);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);
      // when
      handler.correlate(
          List.of(element), CorrelationRequest.builder().variables(Collections.emptyMap()).build());
      // then
      verify(dummyCommand).messageId("456");
    }

    @Test
    void messageIdIsProvided_messageIdIsUUIDifNothingHasBeenSet() {
      // given
      var point = new StandaloneMessageCorrelationPoint("msg1", "=123", null, null);
      var element = mock(InboundConnectorElement.class);
      when(element.correlationPoint()).thenReturn(point);
      when(element.element())
          .thenReturn(new ProcessElement("process1", 0, 0, "element", "default"));

      var dummyCommand = spy(new PublishMessageCommandDummy());
      when(camundaClient.newPublishMessageCommand()).thenReturn(dummyCommand);
      // when
      handler.correlate(
          List.of(element), CorrelationRequest.builder().variables(Collections.emptyMap()).build());
      // then
      verify(dummyCommand).messageId("");
    }
  }
}
