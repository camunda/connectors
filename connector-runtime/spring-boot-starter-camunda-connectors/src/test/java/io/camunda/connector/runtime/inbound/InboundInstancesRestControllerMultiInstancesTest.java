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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.*;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.http.InstanceForwardingHttpClient;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableQuery;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableResponse;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.instances.InstanceAwareModel;
import io.camunda.connector.runtime.instances.service.DefaultInstanceForwardingService;
import io.camunda.connector.runtime.instances.service.InstanceForwardingService;
import java.net.http.HttpClient;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

class InboundInstancesRestControllerMultiInstancesTest {

  private final InboundExecutableRegistry executableRegistry1 =
      Mockito.mock(InboundExecutableRegistry.class);
  private final InboundExecutableRegistry executableRegistry2 =
      Mockito.mock(InboundExecutableRegistry.class);

  private final int port1 = 18080;
  private final int port2 = 18081;

  private final InstanceForwardingHttpClient instanceForwardingHttpClient =
      new InstanceForwardingHttpClient(
          HttpClient.newHttpClient(),
          (path) ->
              List.of("http://localhost:" + port1 + path, "http://localhost:" + port2 + path));

  private static final ExecutableId RANDOM_ID_1 = ExecutableId.fromDeduplicationId("theid1");
  private static final ExecutableId ONLY_IN_RUNTIME_1_ID =
      ExecutableId.fromDeduplicationId("onlyInRuntime1");
  private static final ExecutableId RANDOM_ID_2 = ExecutableId.fromDeduplicationId("theid2");
  private static final ExecutableId ONLY_IN_RUNTIME_2_ID =
      ExecutableId.fromDeduplicationId("onlyInRuntime2");
  private static final ExecutableId RANDOM_ID_3 = ExecutableId.fromDeduplicationId("theid3");
  private static final String TYPE_1 = "webhook";
  private static final String TYPE_2 = "anotherType";
  private static final String TYPE_3_ONLY_IN_RUNTIME_1 = "onlyInRuntime1Type3";

  private static final Activity RUNTIME2_ACTIVITY1 =
      Activity.level(Severity.DEBUG).tag("runtime2").message("myMessage");
  private static final Activity RUNTIME2_ACTIVITY2 =
      Activity.level(Severity.WARNING).tag("runtime2").message("myMessage2");
  private static final Activity RUNTIME1_ACTIVITY1 =
      Activity.level(Severity.INFO).tag("runtime1").message("myMessage");
  private static final Activity RUNTIME1_ACTIVITY2 =
      Activity.level(Severity.ERROR).tag("runtime1").message("myMessage2");

  static class AnotherExecutable implements InboundConnectorExecutable<InboundConnectorContext> {

    @Override
    public void activate(InboundConnectorContext context) throws Exception {}

    @Override
    public void deactivate() throws Exception {}
  }

  static class TestWebhookExecutable implements WebhookConnectorExecutable {

    @Override
    public WebhookResult triggerWebhook(WebhookProcessingPayload payload) throws Exception {
      return null;
    }
  }

  private final TestRestTemplate restTemplate = new TestRestTemplate();

  private ConfigurableApplicationContext context1;
  private ConfigurableApplicationContext context2;

  @AfterEach
  void tearDown() {
    if (context1 != null) context1.close();
    if (context2 != null) context2.close();
  }

  @BeforeEach
  public void init() {
    context1 =
        new SpringApplicationBuilder(TestConnectorRuntimeApplication.class)
            .properties(
                "server.port=" + port1,
                "spring.application.name=instance1",
                "camunda.connector.hostname=instance1",
                "camunda.connector.headless.serviceurl=http://whatever:8080")
            .initializers(
                ctx -> {
                  ((GenericApplicationContext) ctx)
                      .registerBean(InboundExecutableRegistry.class, () -> executableRegistry1);
                  ((GenericApplicationContext) ctx)
                      .registerBean(
                          InstanceForwardingHttpClient.class, () -> instanceForwardingHttpClient);
                  ((GenericApplicationContext) ctx)
                      .registerBean(
                          InstanceForwardingService.class,
                          () ->
                              new DefaultInstanceForwardingService(
                                  instanceForwardingHttpClient, "instance1"));
                })
            .run();

    context2 =
        new SpringApplicationBuilder(TestConnectorRuntimeApplication.class)
            .properties(
                "server.port=" + port2,
                "spring.application.name=instance2",
                "camunda.connector.hostname=instance2",
                "camunda.connector.headless.serviceurl=http://whatever:8080")
            .initializers(
                ctx -> {
                  ((GenericApplicationContext) ctx)
                      .registerBean(InboundExecutableRegistry.class, () -> executableRegistry2);
                  ((GenericApplicationContext) ctx)
                      .registerBean(
                          InstanceForwardingHttpClient.class, () -> instanceForwardingHttpClient);
                  ((GenericApplicationContext) ctx)
                      .registerBean(
                          InstanceForwardingService.class,
                          () ->
                              new DefaultInstanceForwardingService(
                                  instanceForwardingHttpClient, "instance2"));
                })
            .run();

    when(executableRegistry1.getConnectorName(TYPE_1)).thenReturn("Webhook");
    when(executableRegistry1.getConnectorName(TYPE_2)).thenReturn("AnotherType");
    when(executableRegistry2.getConnectorName(TYPE_1)).thenReturn("Webhook");
    when(executableRegistry2.getConnectorName(TYPE_2)).thenReturn("AnotherType");
    when(executableRegistry2.getConnectorName(TYPE_3_ONLY_IN_RUNTIME_1))
        .thenReturn("OnlyInRuntime1");
    when(executableRegistry1.query(any())).thenAnswer(getInstance1Executables());
    when(executableRegistry2.query(any())).thenAnswer(getInstance2Executables());
  }

  private Answer<Object> getInstance1Executables() {
    return invocationOnMock ->
        "UNKNOWN-ID".equals(invocationOnMock.getArgument(0, ActiveExecutableQuery.class).type())
            ? Collections.emptyList()
            : Stream.of(
                    new ActiveExecutableResponse(
                        ONLY_IN_RUNTIME_1_ID,
                        TestWebhookExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of("inbound.context", "myPath", "inbound.type", TYPE_1),
                                new StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElement("ProcessA", 1, 1, "", ""))),
                        Health.up(),
                        Collections.emptyList(),
                        System.currentTimeMillis()),
                    new ActiveExecutableResponse(
                        RANDOM_ID_1,
                        TestWebhookExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of("inbound.context", "myPath", "inbound.type", TYPE_1),
                                new StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElement("ProcessA", 1, 1, "", ""))),
                        Health.up(),
                        Collections.emptyList(),
                        System.currentTimeMillis()),
                    new ActiveExecutableResponse(
                        RANDOM_ID_2,
                        AnotherExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of(
                                    "inbound.other.prop", "myOtherValue", "inbound.type", TYPE_2),
                                new StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElement("ProcessB", 2, 1, "", ""))),
                        Health.up(),
                        Collections.emptyList(),
                        System.currentTimeMillis()),
                    new ActiveExecutableResponse(
                        RANDOM_ID_3,
                        AnotherExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of(
                                    "inbound.other.prop", "myOtherValue2", "inbound.type", TYPE_2),
                                new StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElement("ProcessC", 2, 1, "id1", "")),
                            new InboundConnectorElement(
                                Map.of(
                                    "inbound.other.prop",
                                    "myOtherValue2_2",
                                    "inbound.type",
                                    TYPE_2),
                                new StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression2", "=myPath2", null),
                                new ProcessElement("ProcessC", 2, 1, "id2", ""))),
                        Health.up(),
                        List.of(RUNTIME1_ACTIVITY1, RUNTIME1_ACTIVITY2),
                        System.currentTimeMillis()))
                .filter(
                    response -> {
                      var providedType =
                          invocationOnMock.getArgument(0, ActiveExecutableQuery.class).type();
                      return switch (providedType) {
                        case null -> true;
                        case TYPE_1 ->
                            response.executableId().equals(RANDOM_ID_1)
                                || response.executableId().equals(ONLY_IN_RUNTIME_1_ID);
                        case TYPE_2 ->
                            response.executableId().equals(RANDOM_ID_2)
                                || response.executableId().equals(RANDOM_ID_3);
                        default -> false;
                      };
                    })
                .toList();
  }

  private Answer<Object> getInstance2Executables() {
    return invocationOnMock ->
        "UNKNOWN-ID".equals(invocationOnMock.getArgument(0, ActiveExecutableQuery.class).type())
            ? Collections.emptyList()
            : Stream.of(
                    new ActiveExecutableResponse(
                        ONLY_IN_RUNTIME_2_ID,
                        TestWebhookExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of("inbound.context", "myPath", "inbound.type", TYPE_1),
                                new StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElement("ProcessA", 1, 1, "", ""))),
                        Health.up(),
                        Collections.emptyList(),
                        System.currentTimeMillis()),
                    new ActiveExecutableResponse(
                        RANDOM_ID_1,
                        TestWebhookExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of("inbound.context", "myPath", "inbound.type", TYPE_1),
                                new StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElement("ProcessA", 1, 1, "", ""))),
                        Health.down(new IllegalArgumentException("Test error message")),
                        Collections.emptyList(),
                        System.currentTimeMillis()),
                    new ActiveExecutableResponse(
                        RANDOM_ID_2,
                        AnotherExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of(
                                    "inbound.other.prop", "myOtherValue", "inbound.type", TYPE_2),
                                new StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElement("ProcessB", 2, 1, "", ""))),
                        Health.unknown("Test unknown key", "Test unknown value"),
                        Collections.emptyList(),
                        System.currentTimeMillis()),
                    new ActiveExecutableResponse(
                        RANDOM_ID_3,
                        AnotherExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of(
                                    "inbound.other.prop", "myOtherValue2", "inbound.type", TYPE_2),
                                new StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElement("ProcessC", 2, 1, "id1", "")),
                            new InboundConnectorElement(
                                Map.of(
                                    "inbound.other.prop",
                                    "myOtherValue2_2",
                                    "inbound.type",
                                    TYPE_2),
                                new StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression2", "=myPath2", null),
                                new ProcessElement("ProcessC", 2, 1, "id2", ""))),
                        Health.up(),
                        List.of(RUNTIME2_ACTIVITY1, RUNTIME2_ACTIVITY2),
                        System.currentTimeMillis()))
                .filter(
                    response -> {
                      var providedType =
                          invocationOnMock.getArgument(0, ActiveExecutableQuery.class).type();
                      return switch (providedType) {
                        case null -> true;
                        case TYPE_1 ->
                            response.executableId().equals(RANDOM_ID_1)
                                || response.executableId().equals(ONLY_IN_RUNTIME_2_ID);
                        case TYPE_2 ->
                            response.executableId().equals(RANDOM_ID_2)
                                || response.executableId().equals(RANDOM_ID_3);
                        default -> false;
                      };
                    })
                .toList();
  }

  @Test
  public void shouldReturnConnectorInstances() {
    ResponseEntity<List<ConnectorInstances>> response =
        restTemplate.exchange(
            "http://localhost:" + port1 + "/inbound-instances",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

    List<ConnectorInstances> instance = response.getBody();
    assertEquals(2, instance.size());
    var instance1 = instance.get(0);
    assertEquals(TYPE_1, instance1.connectorId());
    assertEquals("Webhook", instance1.connectorName());
    assertEquals(3, instance1.instances().size());
    var executableIds =
        instance1.instances().stream().map(ActiveInboundConnectorResponse::executableId).toList();
    assertThat(
        executableIds, containsInAnyOrder(RANDOM_ID_1, ONLY_IN_RUNTIME_1_ID, ONLY_IN_RUNTIME_2_ID));
    ActiveInboundConnectorResponse activeInboundConnectorResponse =
        instance1.instances().stream()
            .filter(r -> r.executableId().equals(RANDOM_ID_1))
            .findFirst()
            .get();
    assertEquals(
        Health.down(new IllegalArgumentException("Test error message")),
        activeInboundConnectorResponse.health());
    assertEquals("ProcessA", activeInboundConnectorResponse.elements().getFirst().bpmnProcessId());

    var instance2 = instance.get(1);
    assertEquals(TYPE_2, instance2.connectorId());
    assertEquals("AnotherType", instance2.connectorName());
    assertEquals(2, instance2.instances().size());
    assertEquals(RANDOM_ID_2, instance2.instances().get(0).executableId());
    assertEquals(
        Health.unknown("Test unknown key", "Test unknown value"),
        instance2.instances().get(0).health());
    assertEquals("ProcessB", instance2.instances().get(0).elements().getFirst().bpmnProcessId());
    assertEquals(RANDOM_ID_3, instance2.instances().get(1).executableId());
    assertEquals(Health.up(), instance2.instances().get(1).health());
    assertEquals("ProcessC", instance2.instances().get(1).elements().getFirst().bpmnProcessId());
  }

  @Test
  public void shouldReturn404_whenUnknownConnectorType() {
    var response =
        restTemplate.getForEntity(
            "http://localhost:" + port1 + "/inbound-instances/UNKNOWN-ID", String.class);
    assertThat(
        response.getBody(),
        containsString("Data of type 'ConnectorInstances' with id 'UNKNOWN-ID' not found"));
    assertThat(404, equalTo(response.getStatusCode().value()));
  }

  @Test
  public void shouldReturnSingleConnectorInstance() {
    ResponseEntity<ConnectorInstances> response =
        restTemplate.exchange(
            "http://localhost:" + port1 + "/inbound-instances/" + TYPE_1,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

    var instance = response.getBody();
    assertEquals(TYPE_1, instance.connectorId());
    assertEquals("Webhook", instance.connectorName());
    assertEquals(3, instance.instances().size());
    var executableIds =
        instance.instances().stream().map(ActiveInboundConnectorResponse::executableId).toList();
    assertThat(
        executableIds, containsInAnyOrder(RANDOM_ID_1, ONLY_IN_RUNTIME_1_ID, ONLY_IN_RUNTIME_2_ID));
    ActiveInboundConnectorResponse activeInboundConnectorResponse =
        instance.instances().stream()
            .filter(r -> r.executableId().equals(RANDOM_ID_1))
            .findFirst()
            .get();
    assertEquals(
        Health.down(new IllegalArgumentException("Test error message")),
        activeInboundConnectorResponse.health());
    assertEquals("ProcessA", activeInboundConnectorResponse.elements().getFirst().bpmnProcessId());
  }

  @Test
  public void shouldReturn404_whenUnknownExecutableId() {
    var response =
        restTemplate.getForEntity(
            "http://localhost:"
                + port1
                + "/inbound-instances/"
                + TYPE_1
                + "/executables/UNKNOWN-ID",
            String.class);
    assertThat(
        response.getBody(),
        containsString(
            "Data of type 'ActiveInboundConnectorResponse' with id 'UNKNOWN-ID' not found"));
    assertThat(404, equalTo(response.getStatusCode().value()));
  }

  @Test
  public void shouldReturn404_whenUnknownConnectorTypeAndValidExecutableId() {
    var response =
        restTemplate.getForEntity(
            "http://localhost:"
                + port1
                + "/inbound-instances/UNKNOWN-ID/executables/"
                + RANDOM_ID_1.getId(),
            String.class);
    assertThat(
        response.getBody(),
        containsString(
            "Data of type 'ActiveInboundConnectorResponse' with id 'e379ef68540767f31108eb2fa581814616895690cc92bc5e955e1001743e49b9' not found"));
    assertThat(404, equalTo(response.getStatusCode().value()));
  }

  private static Stream<Arguments> provideExecutableIds() {
    return Stream.of(
        Arguments.of(RANDOM_ID_1),
        Arguments.of(ONLY_IN_RUNTIME_1_ID),
        Arguments.of(ONLY_IN_RUNTIME_2_ID));
  }

  @ParameterizedTest
  @MethodSource("provideExecutableIds")
  public void shouldReturnSingleExecutable(ExecutableId executableId) {
    var response =
        restTemplate.getForEntity(
            "http://localhost:"
                + port1
                + "/inbound-instances/"
                + TYPE_1
                + "/executables/"
                + executableId.getId(),
            ActiveInboundConnectorResponse.class);
    var executable = response.getBody();
    assertEquals(executableId, executable.executableId());
    assertEquals("ProcessA", executable.elements().getFirst().bpmnProcessId());
  }

  @Test
  public void shouldReturnEmptyActivityLogs_whenNoLogs() {
    ResponseEntity<List<InstanceAwareModel.InstanceAwareActivity>> response =
        restTemplate.exchange(
            "http://localhost:"
                + port1
                + "/inbound-instances/"
                + TYPE_1
                + "/executables/"
                + RANDOM_ID_1.getId()
                + "/logs",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    var logs = response.getBody();
    assertTrue(logs.isEmpty());
  }

  @Test
  public void shouldReturnActivityLogs_whenTypeProvided() {
    ResponseEntity<List<InstanceAwareModel.InstanceAwareActivity>> response =
        restTemplate.exchange(
            "http://localhost:"
                + port1
                + "/inbound-instances/"
                + TYPE_2
                + "/executables/"
                + RANDOM_ID_3.getId()
                + "/logs",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    var logs = response.getBody();
    assertEquals(4, logs.size());
    assertThat(
        logs,
        containsInAnyOrder(
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME1_ACTIVITY1.severity(),
                RUNTIME1_ACTIVITY1.tag(),
                RUNTIME1_ACTIVITY1.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME1_ACTIVITY1.message(),
                "instance1"),
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME1_ACTIVITY2.severity(),
                RUNTIME1_ACTIVITY2.tag(),
                RUNTIME1_ACTIVITY2.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME1_ACTIVITY2.message(),
                "instance1"),
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME2_ACTIVITY1.severity(),
                RUNTIME2_ACTIVITY1.tag(),
                RUNTIME2_ACTIVITY1.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME2_ACTIVITY1.message(),
                "instance2"),
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME2_ACTIVITY2.severity(),
                RUNTIME2_ACTIVITY2.tag(),
                RUNTIME2_ACTIVITY2.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME2_ACTIVITY2.message(),
                "instance2")));
  }
}
