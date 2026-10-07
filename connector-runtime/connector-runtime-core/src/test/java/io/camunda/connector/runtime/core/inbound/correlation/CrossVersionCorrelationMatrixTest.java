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
import static org.mockito.Mockito.*;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.runtime.core.TestObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.testutil.command.PublishMessageCommandDummy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;

/**
 * Elements of several process versions sharing one inbound executable (same topic and host),
 * matching the same input. Each test is one case of the cross-version use-case matrix.
 */
class CrossVersionCorrelationMatrixTest {

  private static final Map<String, Object> FULL_PAYLOAD =
      Map.of("value", Map.of("field1", "K1", "field2", "K2", "field3", "R3"));

  private CamundaClient camundaClient;
  private InboundCorrelationHandler handler;
  private PublishMessageCommandDummy command;

  @BeforeEach
  void init() {
    camundaClient = mock(CamundaClient.class);
    handler =
        new InboundCorrelationHandler(
            camundaClient,
            TestObjectMapperSupplier.INSTANCE,
            Duration.ofHours(1),
            new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE));
    command = Mockito.spy(new PublishMessageCommandDummy());
    lenient().when(camundaClient.newPublishMessageCommand()).thenReturn(command);
  }

  private InboundConnectorElement element(
      int version, String messageName, String keyExpression, String resultExpression) {
    var element =
        mock(InboundConnectorElement.class, withSettings().strictness(Strictness.LENIENT));
    when(element.correlationPoint())
        .thenReturn(new StandaloneMessageCorrelationPoint(messageName, keyExpression, null, null));
    when(element.element())
        .thenReturn(
            new ProcessElementWithRuntimeData("process1", version, version, "step", "default"));
    when(element.activationCondition()).thenReturn("");
    when(element.resultExpression()).thenReturn(resultExpression);
    return element;
  }

  private CorrelationResult correlate(
      Map<String, Object> payload, InboundConnectorElement... elements) {
    return handler.correlate(List.of(elements), payload);
  }

  @Test
  void identicalElements_onePublish() {
    var result =
        correlate(
            FULL_PAYLOAD,
            element(1, "M", "=value.field1", "={r: value.field2}"),
            element(2, "M", "=value.field1", "={r: value.field2}"),
            element(3, "M", "=value.field1", "={r: value.field2}"));

    assertThat(result).isInstanceOf(Success.MessagePublished.class);
    verify(camundaClient, times(1)).newPublishMessageCommand();
  }

  @Test
  void differentMessageNames_onePublishPerName_latestFirst() {
    var result =
        correlate(
            FULL_PAYLOAD,
            element(1, "M", "=value.field1", "={r: value.field2}"),
            element(2, "N", "=value.field1", "={r: value.field2}"),
            element(3, "O", "=value.field1", "={r: value.field2}"));

    assertThat(result).isInstanceOf(Success.MessagePublished.class);
    verify(camundaClient, times(3)).newPublishMessageCommand();
    var inOrder = inOrder(command);
    inOrder.verify(command).messageName("O");
    inOrder.verify(command).messageName("N");
    inOrder.verify(command).messageName("M");
  }

  @Test
  void differentCorrelationKeyPayloadExpressions_onePublishPerExpression() {
    var result =
        correlate(
            FULL_PAYLOAD,
            element(1, "M", "=value.field1", "={r: value.field2}"),
            element(2, "M", "=value.field2", "={r: value.field2}"),
            element(3, "M", "=value.field2", "={r: value.field2}"));

    assertThat(result).isInstanceOf(Success.MessagePublished.class);
    verify(camundaClient, times(2)).newPublishMessageCommand();
    verify(command).correlationKey("K2");
    verify(command).correlationKey("K1");
  }

  @Test
  void sameMessageDifferentResultExpression_tooManyMatchingElements() {
    var result =
        correlate(
            FULL_PAYLOAD,
            element(1, "M", "=value.field1", "={r: value.field2}"),
            element(2, "M", "=value.field1", "={r: value.field3}"),
            element(3, "M", "=value.field1", "={r: value.field2}"));

    assertThat(result).isInstanceOf(Failure.InvalidInput.class);
    assertThat(((Failure.InvalidInput) result).message())
        .contains("migrate or cancel them")
        .contains("active-versions-enabled=false");
    verifyNoInteractions(camundaClient);
  }

  @Test
  void differentProcessSideKeys_identicalConnectorSide_onePublish() {
    // The process-side correlation key (=orderId vs =otherId) lives on the BPMN subscription; the
    // connector element only carries the message name and payload key expression, identical here.
    var result =
        correlate(
            FULL_PAYLOAD,
            element(1, "N", "=value.field1", "={r: value.field2}"),
            element(2, "N", "=value.field1", "={r: value.field2}"));

    assertThat(result).isInstanceOf(Success.MessagePublished.class);
    verify(camundaClient, times(1)).newPublishMessageCommand();
  }

  @Test
  void incompatibleGroupOfOlderVersionsOnly_doesNotBlockTheLatestVersion() {
    var result =
        correlate(
            FULL_PAYLOAD,
            element(1, "M", "=value.field1", "={r: value.field2}"),
            element(2, "M", "=value.field1", "={r: value.field3}"),
            element(3, "O", "=value.field1", "={r: value.field2}"));

    assertThat(result).isInstanceOf(Success.MessagePublished.class);
    verify(camundaClient, times(1)).newPublishMessageCommand();
    verify(command).messageName("O");
  }

  @Test
  void incompatibleGroupOfOlderVersionsOnly_latestVersionNotMatching_notMatched() {
    var latest = element(3, "O", "=value.field1", "={r: value.field2}");
    when(latest.activationCondition()).thenReturn("=false");

    var result =
        correlate(
            FULL_PAYLOAD,
            element(1, "M", "=value.field1", "={r: value.field2}"),
            element(2, "M", "=value.field1", "={r: value.field3}"),
            latest);

    assertThat(result).isInstanceOf(Failure.ActivationConditionNotMet.class);
    verifyNoInteractions(camundaClient);
  }

  @Test
  void latestVersionActivationConditionInvalid_olderVersionStillPublished() {
    var latest = element(3, "O", "=value.field1", "={r: value.field2}");
    when(latest.activationCondition()).thenReturn("=");

    var result =
        correlate(FULL_PAYLOAD, element(1, "M", "=value.field1", "={r: value.field2}"), latest);

    assertThat(result).isInstanceOf(Success.MessagePublished.class);
    verify(camundaClient, times(1)).newPublishMessageCommand();
    verify(command).messageName("M");
  }

  @Test
  void latestVersionKeyMissing_olderVersionStillPublished() {
    var result =
        correlate(
            Map.of("value", Map.of("field1", "K1")),
            element(1, "M", "=value.field1", "={r: value.field2}"),
            element(3, "M", "=value.field2", "={r: value.field2}"));

    assertThat(result).isInstanceOf(Success.MessagePublished.class);
    verify(camundaClient, times(1)).newPublishMessageCommand();
    verify(command).correlationKey("K1");
  }

  @Test
  void noVersionCanResolveItsKey_latestVersionFailureReturned() {
    var result =
        correlate(
            Map.of("value", Map.of()),
            element(1, "M", "=value.field1", "={r: value.field2}"),
            element(3, "M", "=value.field2", "={r: value.field2}"));

    assertThat(result).isInstanceOf(Failure.InvalidInput.class);
    assertThat(((Failure.InvalidInput) result).message()).contains("value.field2");
    verifyNoInteractions(camundaClient);
  }
}
