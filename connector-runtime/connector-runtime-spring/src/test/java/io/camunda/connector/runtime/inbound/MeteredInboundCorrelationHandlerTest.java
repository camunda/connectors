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
package io.camunda.connector.runtime.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.runtime.TestObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeteredInboundCorrelationHandlerTest {

  private static final Duration DEFAULT_TTL = Duration.ofHours(2);

  @Mock private ConnectorsInboundMetrics metrics;

  private CamundaClient camundaClient;
  private MeteredInboundCorrelationHandler handler;
  private InboundConnectorElement element;

  @BeforeEach
  void setUp() {
    camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    handler =
        new MeteredInboundCorrelationHandler(
            camundaClient, TestObjectMapperSupplier.INSTANCE, DEFAULT_TTL, metrics);
    element = mock(InboundConnectorElement.class);
  }

  /** Configures the element stubs needed when the correlation path is actually reached. */
  private void stubElementForCorrelation() {
    when(element.correlationPoint()).thenReturn(new StartEventCorrelationPoint("process1", 0, 0));
    when(element.element())
        .thenReturn(new ProcessElementWithRuntimeData("process1", 0, 0, "element", "default"));
  }

  @Test
  void correlate_success_incrementsTriggerAndSuccess() {
    // given
    stubElementForCorrelation();

    // when
    var result = handler.correlate(List.of(element), Map.of());

    // then
    assertThat(result).isInstanceOf(CorrelationResult.Success.class);
    verify(metrics).increaseTrigger(element);
    verify(metrics).increaseCorrelationSuccess(element);
    verify(metrics, never()).increaseCorrelationFailure(any());
    verify(metrics, never()).increaseActivationConditionFailure(any());
  }

  @Test
  void correlate_activationConditionNotMet_incrementsTriggerAndConditionFailed() {
    // given — activation condition never matches; correlation point is never reached
    when(element.activationCondition()).thenReturn("=value=\"never\"");
    when(element.consumeUnmatchedEvents()).thenReturn(false);

    // when
    var result = handler.correlate(List.of(element), Map.of("value", "something-else"));

    // then
    assertThat(result).isInstanceOf(CorrelationResult.Failure.ActivationConditionNotMet.class);
    verify(metrics).increaseTrigger(element);
    verify(metrics).increaseActivationConditionFailure(element);
    verify(metrics, never()).increaseCorrelationSuccess(any());
    verify(metrics, never()).increaseCorrelationFailure(any());
  }

  @Test
  void correlate_zeebeFailure_incrementsTriggerAndCorrelationFailed() {
    // given — Zeebe throws, handler catches it and returns CorrelationResult.Failure.Other
    stubElementForCorrelation();
    when(camundaClient.newCreateInstanceCommand())
        .thenThrow(new RuntimeException("Zeebe unavailable"));

    // when
    var result = handler.correlate(List.of(element), Map.of());

    // then
    assertThat(result).isInstanceOf(CorrelationResult.Failure.Other.class);
    verify(metrics).increaseTrigger(element);
    verify(metrics).increaseCorrelationFailure(element);
    verify(metrics, never()).increaseCorrelationSuccess(any());
    verify(metrics, never()).increaseActivationConditionFailure(any());
  }

  @Test
  void canActivate_calledDirectly_doesNotIncrementAnyCounter() {
    // given — connectors may call canActivate() manually; it must not affect metrics
    when(element.activationCondition()).thenReturn("=value=\"never\"");

    // when
    handler.canActivate(List.of(element), Map.of("value", "something-else"));

    // then
    verifyNoInteractions(metrics);
  }
}
