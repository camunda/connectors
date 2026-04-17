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
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.api.inbound.Health.Status;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.inbound.*;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableEvent.ProcessStateChanged;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InboundExecutableRegistryTest {

  public static final String RANDOM_STRING = "thededuplicationId";
  private static final ScheduledExecutorService scheduledExecutorService =
      Executors.newSingleThreadScheduledExecutor();
  private static final ExecutableId RANDOM_ID = ExecutableId.fromDeduplicationId(RANDOM_STRING);
  private InboundConnectorFactory factory;
  private InboundConnectorContextFactory contextFactory;
  private BatchExecutableProcessor batchProcessor;
  private ActivityLogRegistry activityLogRegistry = new ActivityLogRegistry();
  private InboundExecutableRegistryImpl registry;

  @BeforeEach
  public void prepareMocks() {
    factory = mock(InboundConnectorFactory.class);
    when(factory.getActiveConfigurations()).thenReturn(List.of());
    contextFactory = mock(DefaultInboundConnectorContextFactory.class);
    var inboundMetrics = mock(ConnectorsInboundMetrics.class);
    batchProcessor =
        new BatchExecutableProcessor(
            factory, contextFactory, inboundMetrics, null, activityLogRegistry);
    registry = new InboundExecutableRegistryImpl(factory, batchProcessor, activityLogRegistry);
  }

  @Test
  public void invalidDeduplicationConfig_propertyMismatch_shouldYieldInvalidDefinition() {
    // given
    var elementId = "elementId";
    var element1 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant"));
    var element2 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type2"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);
    when(factory.getInstance(any())).thenReturn(executable);

    // when
    registry.handleEvent(
        new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element1, element2))));

    // then
    var result = registry.query(new ActiveExecutableQuery(null, elementId, null, null));
    assertThat(result.getFirst().health().getStatus()).isEqualTo(Status.DOWN);
    assertThat(result.getFirst().health().getError().message())
        .contains("Invalid connector definition: All elements in a group must have the same type");
  }

  @Test
  public void validDeduplicationConfig_runtimePropertyMismatch_shouldActivateNormally()
      throws Exception {
    // given
    // different MESSAGE_TTL property
    var elementId = "elementId";
    var element1 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1", Keywords.MESSAGE_TTL, "PT2S"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant"));
    var element2 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1", Keywords.MESSAGE_TTL, "PT1S"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);
    when(factory.getInstance(any())).thenReturn(executable);

    // when
    registry.handleEvent(
        new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element1, element2))));

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
            new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);
    var context = mock(InboundConnectorContextImpl.class);
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(context);

    when(factory.getInstance(any())).thenReturn(executable);

    // when
    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element))));

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
            new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);
    when(factory.getInstance(any())).thenReturn(executable);
    doThrow(new RuntimeException("failed")).when(executable).activate(any());

    var mockContext = mock(InboundConnectorContextImpl.class);
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(mockContext);

    doThrow(new RuntimeException("failed")).when(executable).activate(any());

    // when
    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element))));

    // then
    var result = registry.query(new ActiveExecutableQuery(null, elementId, null, null));
    assertThat(result.getFirst().health().getStatus()).isEqualTo(Status.DOWN);
    assertThat(result.getFirst().health().getError().message()).contains("failed");
  }

  @Test
  public void activationFailure_batch_shouldRollbackOtherConnectors() throws Exception {
    // given
    var processId = "processId";
    var element1 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type"),
            new StartEventCorrelationPoint(processId, 0, 0),
            new ProcessElementWithRuntimeData(processId, 0, 0, "element1", "tenant"));
    var element2 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type"),
            new StartEventCorrelationPoint(processId, 0, 0),
            new ProcessElementWithRuntimeData(processId, 0, 0, "element2", "tenant"));

    var executable1 = mock(InboundConnectorExecutable.class);
    var executable2 = mock(InboundConnectorExecutable.class);

    when(factory.getInstance(any())).thenReturn(executable1).thenReturn(executable2);
    var mockContext = mock(InboundConnectorContextImpl.class);
    when(mockContext.connectorElements()).thenReturn(List.of(element1, element2));
    when(mockContext.getDefinition())
        .thenReturn(new InboundConnectorDefinition("type", "tenant", "id", null));
    when(mockContext.getHealth()).thenReturn(Health.up());
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(mockContext);

    doThrow(new RuntimeException("failed")).when(executable2).activate(mockContext);

    // when
    registry.handleEvent(
        new ProcessStateChanged(processId, "tenant", Map.of(0L, List.of(element1, element2))));

    // then
    verify(executable1).activate(mockContext);
    verify(executable2).activate(mockContext);
    verify(executable1).deactivate();

    var results = registry.query(new ActiveExecutableQuery(null, null, null, null));
    assertThat(results.size()).isEqualTo(2);
    assertThat(results.stream().allMatch(r -> r.health().getStatus() == Status.DOWN)).isTrue();
  }

  @Test
  public void connectorNotFound_batch_shouldNotRollbackOtherConnectors() throws Exception {
    // we want to allow other connectors to be activated even if one is not registered in the
    // connector factory - this way we can also support hybrid mode.

    // given
    var processId = "processId";
    var element1 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type"),
            new StartEventCorrelationPoint(processId, 0, 0),
            new ProcessElementWithRuntimeData(processId, 0, 0, "element1", "tenant"));
    var element2 =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type"),
            new StartEventCorrelationPoint(processId, 0, 0),
            new ProcessElementWithRuntimeData(processId, 0, 0, "element2", "tenant"));

    var executable1 = mock(InboundConnectorExecutable.class);

    when(factory.getInstance(any()))
        .thenReturn(executable1)
        .thenThrow(new NoSuchElementException("not registered"));
    var mockContext = mock(InboundConnectorContextImpl.class);
    when(mockContext.connectorElements()).thenReturn(List.of(element1, element2));
    when(mockContext.getDefinition())
        .thenReturn(new InboundConnectorDefinition("type", "tenant", "id", null));
    when(mockContext.getHealth()).thenReturn(Health.up());
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(mockContext);

    // when
    registry.handleEvent(
        new ProcessStateChanged(processId, "tenant", Map.of(0L, List.of(element1, element2))));

    // then
    verify(executable1).activate(mockContext);
    verifyNoMoreInteractions(executable1);

    var results = registry.query(new ActiveExecutableQuery(null, null, null, null));
    assertThat(results.size()).isEqualTo(2);
    // One should be healthy (activated), one should be down (not registered)
    assertThat(results.stream().filter(r -> r.health().getStatus() == Status.UP).count())
        .isEqualTo(1);
    assertThat(results.stream().filter(r -> r.health().getStatus() == Status.DOWN).count())
        .isEqualTo(1);
  }

  @Test
  public void missingConnector_shouldYieldConnectorNotRegistered() {
    // given
    var elementId = "elementId";
    var element =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "unknown-type"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant"));
    when(factory.getInstance(any()))
        .thenThrow(new NoSuchElementException("Connector unknown-type not registered"));

    // when
    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element))));

    // then
    var result = registry.query(new ActiveExecutableQuery(null, elementId, null, null));
    assertThat(result.getFirst().health().getStatus()).isEqualTo(Status.DOWN);
    assertThat(result.getFirst().health().getError().message())
        .contains("Connector unknown-type not registered");
  }

  @Test
  public void verify_activatedConnectorIsCancelledAndRestarted() throws Exception {

    var elementId = "elementId";
    var element1 =
        spy(
            new InboundConnectorElement(
                Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1", Keywords.MESSAGE_TTL, "PT2S"),
                new StartEventCorrelationPoint("processId", 0, 0),
                new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant")));
    when(element1.deduplicationId(any())).thenReturn(RANDOM_STRING);

    var executable = mock(InboundConnectorExecutable.class);
    var context = mock(InboundConnectorManagementContext.class);
    var definitions = mock(InboundConnectorDefinition.class);

    when(definitions.deduplicationId()).thenReturn(RANDOM_STRING);
    when(factory.getInstance(any())).thenReturn(executable);
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(context);
    when(context.getDefinition()).thenReturn(definitions);
    when(definitions.type()).thenReturn("type1");
    // when
    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element1))));
    registry.handleEvent(
        new InboundExecutableEvent.Cancelled(
            RANDOM_ID,
            ConnectorRetryException.builder()
                .retries(3)
                .backoffDuration(Duration.ofSeconds(1))
                .build()));

    // then
    verify(executable, timeout(5000).times(1)).deactivate();
    verify(executable, timeout(5000).times(2)).activate(any());
  }

  @Test
  public void verify_activatedConnectorIsCancelledAndActivateCancelAgain() throws Exception {
    var elementId = "elementId";
    var element1 =
        spy(
            new InboundConnectorElement(
                Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1", Keywords.MESSAGE_TTL, "PT2S"),
                new StartEventCorrelationPoint("processId", 0, 0),
                new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant")));

    when(element1.deduplicationId(any())).thenReturn(RANDOM_STRING);

    var executable = mock(InboundConnectorExecutable.class);
    var context =
        new InboundConnectorContextImpl(
            mock(SecretProvider.class),
            mock(ValidationProvider.class),
            mock(InboundConnectorDetails.ValidInboundConnectorDetails.class),
            null,
            t -> registry.handleEvent(new InboundExecutableEvent.Cancelled(RANDOM_ID, t)),
            new ObjectMapper(),
            null);

    when(factory.getInstance(any())).thenReturn(executable);
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(context);
    doAnswer(
            invocationOnMock -> {
              context.cancel(
                  ConnectorRetryException.builder()
                      .retries(3)
                      .backoffDuration(Duration.ofSeconds(1))
                      .build());
              return null;
            })
        .when(executable)
        .activate(any());

    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element1))));

    // then
    verify(executable, timeout(5000).times(1)).activate(any());
    verify(executable, timeout(5000).times(0)).deactivate();
  }

  @Test
  public void verify_activatedConnectorIsCancelledAndRestartedFromTheContext() throws Exception {
    var elementId = "elementId";
    var element1 =
        spy(
            new InboundConnectorElement(
                Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1", Keywords.MESSAGE_TTL, "PT2S"),
                new StartEventCorrelationPoint("processId", 0, 0),
                new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant")));
    when(element1.deduplicationId(any())).thenReturn(RANDOM_STRING);

    var connectorDetails = mock(ValidInboundConnectorDetails.class);
    when(connectorDetails.deduplicationId()).thenReturn(RANDOM_STRING);

    var executable = mock(InboundConnectorExecutable.class);
    var context =
        new InboundConnectorContextImpl(
            mock(SecretProvider.class),
            mock(ValidationProvider.class),
            connectorDetails,
            null,
            t -> registry.handleEvent(new InboundExecutableEvent.Cancelled(RANDOM_ID, t)),
            new ObjectMapper(),
            null);

    when(factory.getInstance(any())).thenReturn(executable);
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(context);
    // when
    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element1))));

    context.cancel(
        ConnectorRetryException.builder()
            .retries(3)
            .backoffDuration(Duration.ofSeconds(1))
            .build());

    // then
    verify(executable, timeout(5000).times(2)).activate(any());
    verify(executable, timeout(5000).times(1)).deactivate();
  }

  @Test
  public void verify_activatedConnectorIsCancelledAndRestartFromTheContextFails() throws Exception {
    var elementId = "elementId";
    var element1 =
        spy(
            new InboundConnectorElement(
                Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1", Keywords.MESSAGE_TTL, "PT2S"),
                new StartEventCorrelationPoint("processId", 0, 0),
                new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant")));
    when(element1.deduplicationId(any())).thenReturn(RANDOM_STRING);

    var executable = mock(InboundConnectorExecutable.class);
    var definition = mock(InboundConnectorDefinition.class);
    var context =
        spy(
            new InboundConnectorContextImpl(
                mock(SecretProvider.class),
                mock(ValidationProvider.class),
                mock(InboundConnectorDetails.ValidInboundConnectorDetails.class),
                null,
                t -> registry.handleEvent(new InboundExecutableEvent.Cancelled(RANDOM_ID, t)),
                new ObjectMapper(),
                null));

    doNothing().doThrow(new Exception()).when(executable).activate(any());
    when(definition.deduplicationId()).thenReturn(RANDOM_STRING);
    doReturn(definition).when(context).getDefinition();
    when(factory.getInstance(any())).thenReturn(executable);
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(context);
    // when
    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element1))));

    context.cancel(
        ConnectorRetryException.builder()
            .retries(3)
            .backoffDuration(Duration.ofSeconds(1))
            .build());

    // then
    verify(executable, timeout(5000).times(4)).activate(any());
    verify(executable, timeout(5000).times(1)).deactivate();
  }

  @Test
  public void verify_activatedConnectorIsCancelledAndCouldNotBeRestarted() throws Exception {

    var elementId = "elementId";
    var element1 =
        spy(
            new InboundConnectorElement(
                Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1", Keywords.MESSAGE_TTL, "PT2S"),
                new StartEventCorrelationPoint("processId", 0, 0),
                new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant")));
    when(element1.deduplicationId(any())).thenReturn(RANDOM_STRING);

    var executable = mock(InboundConnectorExecutable.class);
    var context = mock(InboundConnectorManagementContext.class);
    var definitions = mock(InboundConnectorDefinition.class);

    // when
    when(definitions.deduplicationId()).thenReturn(RANDOM_STRING);
    when(factory.getInstance(any())).thenReturn(executable);
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(context);
    when(context.getDefinition()).thenReturn(definitions);
    when(definitions.type()).thenReturn("type1");
    doNothing().doThrow(new Exception()).when(executable).activate(any());

    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element1))));

    registry.handleEvent(
        new InboundExecutableEvent.Cancelled(
            RANDOM_ID,
            ConnectorRetryException.builder()
                .retries(3)
                .backoffDuration(Duration.ofSeconds(1))
                .build()));

    // then
    verify(executable).deactivate();
    verify(executable, timeout(5000).times(4)).activate(any());
  }

  @Test
  public void verify_activatedConnectorIsCancelledAndCouldNotBeRestartedButSecondRetryWorks()
      throws Exception {

    var elementId = "elementId";
    var element1 =
        spy(
            new InboundConnectorElement(
                Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1", Keywords.MESSAGE_TTL, "PT2S"),
                new StartEventCorrelationPoint("processId", 0, 0),
                new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant")));
    when(element1.deduplicationId(any())).thenReturn(RANDOM_STRING);

    var executable = mock(InboundConnectorExecutable.class);
    var context = mock(InboundConnectorManagementContext.class);
    var definitions = mock(InboundConnectorDefinition.class);

    // when
    when(definitions.deduplicationId()).thenReturn(RANDOM_STRING);
    when(factory.getInstance(any())).thenReturn(executable);
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(context);
    when(context.getDefinition()).thenReturn(definitions);
    when(definitions.type()).thenReturn("type1");
    doNothing().doThrow(new Exception()).doNothing().when(executable).activate(any());

    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element1))));

    registry.handleEvent(
        new InboundExecutableEvent.Cancelled(
            RANDOM_ID,
            ConnectorRetryException.builder()
                .retries(3)
                .backoffDuration(Duration.ofSeconds(1))
                .build()));

    // then
    verify(executable).deactivate();
    verify(executable, timeout(5000).times(3)).activate(any());
  }

  // -------------------------------------------------------------------------
  // Activity log tests
  // -------------------------------------------------------------------------

  @Test
  public void activityLog_successfulActivation_shouldContainLifecycleEntry() throws Exception {
    // given
    var elementId = "elementId";
    var element =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);
    var context = mock(InboundConnectorManagementContext.class);
    // Required so matchesQuery can match on elementId/bpmnProcessId/type/tenantId
    when(context.connectorElements()).thenReturn(List.of(element));
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(context);
    when(factory.getInstance(any())).thenReturn(executable);

    // when
    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element))));

    // then
    var result = registry.query(new ActiveExecutableQuery("id", elementId, "type1", "tenant"));
    assertThat(result.getFirst().logs())
        .hasSize(1)
        .first()
        .satisfies(
            log -> {
              assertThat(log.severity()).isEqualTo(Severity.INFO);
              assertThat(log.tag()).isEqualTo(ActivityLogTag.LIFECYCLE);
              assertThat(log.message()).contains("Activated inbound connector");
              assertThat(log.message()).contains("type1");
            });
  }

  @Test
  public void activityLog_activationFailure_shouldNotContainAnyEntries() throws Exception {
    // given
    var elementId = "elementId";
    var element =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);
    var context = mock(InboundConnectorManagementContext.class);
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(context);
    when(factory.getInstance(any())).thenReturn(executable);
    doThrow(new RuntimeException("activation failed")).when(executable).activate(any());

    // when
    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element))));

    // then
    var result = registry.query(new ActiveExecutableQuery(null, elementId, null, null));
    assertThat(result.getFirst().logs()).isEmpty();
  }

  @Test
  public void activityLog_deactivation_shouldContainBothActivationAndDeactivationEntries()
      throws Exception {
    // given
    var elementId = "elementId";
    var element =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);
    var context = mock(InboundConnectorManagementContext.class);
    when(context.getDefinition())
        .thenReturn(new InboundConnectorDefinition("type1", "tenant", "id", null));
    when(context.connectorElements()).thenReturn(List.of(element));
    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(context);
    when(factory.getInstance(any())).thenReturn(executable);

    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element))));
    var executableId =
        registry
            .query(new ActiveExecutableQuery(null, elementId, null, null))
            .getFirst()
            .executableId();

    // when - send empty process state to trigger deactivation of all connectors in this process
    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of()));

    // then
    var logs = activityLogRegistry.getLogs(executableId);
    assertThat(logs).hasSize(2);
    assertThat(logs)
        .extracting(Activity::message)
        .anySatisfy(msg -> assertThat(msg).contains("Activated"))
        .anySatisfy(msg -> assertThat(msg).contains("Deactivated"));
    assertThat(logs).extracting(Activity::tag).containsOnly(ActivityLogTag.LIFECYCLE);
  }

  @Test
  public void activityLog_connectorWritesLog_shouldAppearAlongsideRuntimeLogs() throws Exception {
    // given
    var elementId = "elementId";
    var element =
        new InboundConnectorElement(
            Map.of(Keywords.INBOUND_TYPE_KEYWORD, "type1"),
            new StartEventCorrelationPoint("processId", 0, 0),
            new ProcessElementWithRuntimeData("id", 0, 0, elementId, "tenant"));
    var executable = mock(InboundConnectorExecutable.class);

    // The deduplication ID in legacy mode is: tenantId + "-" + processDefinitionKey + "-" +
    // elementId
    var connectorDetails = mock(ValidInboundConnectorDetails.class);
    when(connectorDetails.deduplicationId()).thenReturn("tenant-0-elementId");
    when(connectorDetails.rawPropertiesWithoutKeywords()).thenReturn(Map.of());
    when(connectorDetails.connectorElements()).thenReturn(List.of(element));

    var realContext =
        new InboundConnectorContextImpl(
            mock(SecretProvider.class),
            mock(ValidationProvider.class),
            connectorDetails,
            null,
            t -> {},
            new ObjectMapper(),
            activityLogRegistry);

    when(contextFactory.createContext(any(), any(), any(), any())).thenReturn(realContext);
    when(factory.getInstance(any())).thenReturn(executable);
    doAnswer(
            invocation -> {
              realContext.log(
                  a ->
                      a.withSeverity(Severity.DEBUG)
                          .withTag(ActivityLogTag.CONSUMER)
                          .withMessage("Custom connector log entry"));
              return null;
            })
        .when(executable)
        .activate(any());

    // when
    registry.handleEvent(new ProcessStateChanged("id", "tenant", Map.of(0L, List.of(element))));

    // then
    var result = registry.query(new ActiveExecutableQuery(null, elementId, null, null));
    var logs = result.getFirst().logs();
    assertThat(logs).hasSize(2);
    assertThat(logs)
        .extracting(Activity::tag)
        .containsExactlyInAnyOrder(ActivityLogTag.LIFECYCLE, ActivityLogTag.CONSUMER);
    assertThat(logs)
        .extracting(Activity::message)
        .anySatisfy(msg -> assertThat(msg).contains("Activated inbound connector"))
        .anySatisfy(msg -> assertThat(msg).isEqualTo("Custom connector log entry"));
  }
}
