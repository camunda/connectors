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
package io.camunda.connector.runtime.inbound.executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.Health.Status;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.inbound.DefaultInboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableEvent.Activated;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InboundExecutableRegistryTest {

  private InboundConnectorFactory factory;
  private InboundConnectorContextFactory contextFactory;
  private BatchExecutableProcessor batchProcessor;
  private InboundExecutableRegistryImpl registry;

  @BeforeEach
  public void prepareMocks() {
    factory = mock(InboundConnectorFactory.class);
    contextFactory = mock(DefaultInboundConnectorContextFactory.class);
    batchProcessor = new BatchExecutableProcessor(factory, contextFactory, null, null);
    registry = new InboundExecutableRegistryImpl(factory, batchProcessor);
  }

  @Test
  public void invalidDeduplicationConfig_propertyMismatch_shouldYieldInvalidDefinition() {
    // given
    var elementId = "elementId";
    var element1 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElement("id", 0, 0, elementId, "tenant"));
    var element2 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type2"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElement("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);
    when(factory.getInstance(any())).thenReturn(executable);

    // when
    registry.handleEvent(new Activated("tenant", 0, List.of(element1, element2)));

    // then
    var result = registry.query(new ActiveExecutableQuery(null, elementId, null, null));
    assertThat(result.getFirst().health().getStatus()).isEqualTo(Status.DOWN);
    assertThat(result.getFirst().health().getError().message())
        .isEqualTo("Invalid connector definition: All elements in a group must have the same type");
  }

  @Test
  public void validDeduplicationConfig_runtimePropertyMismatch_shouldActivateNormally()
      throws Exception {
    // given
    // different MESSAGE_ID_EXPRESSION property
    var elementId = "elementId";
    var element1 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1", Keywords.MESSAGE_ID_EXPRESSION, "PT2S"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElement("id", 0, 0, elementId, "tenant"));
    var element2 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1", Keywords.MESSAGE_ID_EXPRESSION, "PT1S"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElement("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);
    when(factory.getInstance(any())).thenReturn(executable);

    // when
    registry.handleEvent(new Activated("tenant", 0, List.of(element1, element2)));

    // then
    verify(executable).activate(any());
  }

  @Test
  public void validScenario_shouldActivateNormally() throws Exception {
    // given
    var elementId = "elementId";
    var element =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElement("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);
    var context = mock(InboundConnectorContextImpl.class);
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(context);

    when(factory.getInstance(any())).thenReturn(executable);

    // when
    registry.handleEvent(new Activated("tenant", 0, List.of(element)));

    // then
    verify(executable).activate(context);
  }

  @Test
  public void activationFailure_shouldYieldFailedToActivate() throws Exception {
    // given
    var elementId = "elementId";
    var element =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElement("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);
    when(factory.getInstance(any())).thenReturn(executable);
    doThrow(new RuntimeException("failed")).when(executable).activate(any());

    // when
    registry.handleEvent(new Activated("tenant", 0, List.of(element)));

    // then
    var result = registry.query(new ActiveExecutableQuery(null, elementId, null, null));
    assertThat(result.getFirst().health().getStatus()).isEqualTo(Status.DOWN);
    assertThat(result.getFirst().health().getError().message()).isEqualTo("failed");
  }

  @Test
  public void missingConnector_shouldYieldConnectorNotRegistered() {
    // given
    var elementId = "elementId";
    var element =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "unknown-type"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElement("id", 0, 0, elementId, "tenant"));
    when(factory.getInstance(any()))
        .thenThrow(new NoSuchElementException("Connector unknown-type not registered"));

    // when
    registry.handleEvent(new Activated("tenant", 0, List.of(element)));

    // then
    var result = registry.query(new ActiveExecutableQuery(null, elementId, null, null));
    assertThat(result.getFirst().health().getStatus()).isEqualTo(Status.DOWN);
    assertThat(result.getFirst().health().getError().message())
        .isEqualTo("Connector unknown-type not registered");
  }
}
