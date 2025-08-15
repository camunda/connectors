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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.*;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.http.InstanceForwardingHttpClient;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableQuery;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableResponse;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.instances.service.DefaultInstanceForwardingService;
import io.camunda.connector.runtime.instances.service.InstanceForwardingService;
import java.net.http.HttpClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

abstract class BaseMultiInstancesTest {
  final InboundExecutableRegistry executableRegistry1 =
      Mockito.mock(InboundExecutableRegistry.class);
  final InboundExecutableRegistry executableRegistry2 =
      Mockito.mock(InboundExecutableRegistry.class);

  final int port1 = 18080;
  final int port2 = 18081;

  final InstanceForwardingHttpClient instanceForwardingHttpClient =
      new InstanceForwardingHttpClient(
          HttpClient.newHttpClient(),
          (path) -> List.of("http://localhost:" + port1 + path, "http://localhost:" + port2 + path),
          ConnectorsObjectMapperSupplier.getCopy());

  static final ExecutableId RANDOM_ID_1 = ExecutableId.fromDeduplicationId("theid1");
  static final ExecutableId ONLY_IN_RUNTIME_1_ID =
      ExecutableId.fromDeduplicationId("onlyInRuntime1");
  static final ExecutableId RANDOM_ID_2 = ExecutableId.fromDeduplicationId("theid2");
  static final ExecutableId ONLY_IN_RUNTIME_2_ID =
      ExecutableId.fromDeduplicationId("onlyInRuntime2");
  static final ExecutableId RANDOM_ID_3 = ExecutableId.fromDeduplicationId("theid3");
  static final String TYPE_1 = "webhook";
  static final String TYPE_2 = "anotherType";
  static final String TYPE_3_ONLY_IN_RUNTIME_1 = "onlyInRuntime1Type3";

  static final Activity RUNTIME2_ACTIVITY1 =
      Activity.level(Severity.DEBUG).tag("runtime2").message("myMessage");
  static final Activity RUNTIME2_ACTIVITY2 =
      Activity.level(Severity.WARNING).tag("runtime2").message("myMessage2");
  static final Activity RUNTIME1_ACTIVITY1 =
      Activity.level(Severity.INFO).tag("runtime1").message("myMessage");
  static final Activity RUNTIME1_ACTIVITY2 =
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

  protected final TestRestTemplate restTemplate = new TestRestTemplate();

  ConfigurableApplicationContext context1;
  ConfigurableApplicationContext context2;

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

  Answer<Object> getInstance1Executables() {
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
                                new MessageCorrelationPoint.StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElementWithRuntimeData(
                                    "ProcessA", 1, 1, "elementIdProcessA", "tenantId"))),
                        Health.up(),
                        List.of(),
                        System.currentTimeMillis()),
                    new ActiveExecutableResponse(
                        RANDOM_ID_1,
                        TestWebhookExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of("inbound.context", "myPath", "inbound.type", TYPE_1),
                                new MessageCorrelationPoint.StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElementWithRuntimeData(
                                    "ProcessA", 1, 1, "elementId2ProcessA", "tenantId"))),
                        Health.up(),
                        List.of(),
                        System.currentTimeMillis()),
                    new ActiveExecutableResponse(
                        RANDOM_ID_2,
                        AnotherExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of(
                                    "inbound.other.prop", "myOtherValue", "inbound.type", TYPE_2),
                                new MessageCorrelationPoint.StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElementWithRuntimeData(
                                    "ProcessB", 2, 1, "", "tenantId"))),
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
                                new MessageCorrelationPoint.StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElementWithRuntimeData(
                                    "ProcessC", 2, 1, "id1", "tenantId")),
                            new InboundConnectorElement(
                                Map.of(
                                    "inbound.other.prop",
                                    "myOtherValue2_2",
                                    "inbound.type",
                                    TYPE_2),
                                new MessageCorrelationPoint.StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression2", "=myPath2", null),
                                new ProcessElementWithRuntimeData(
                                    "ProcessC", 2, 1, "id2", "tenantId"))),
                        Health.unknown("Test unknown key", "Test unknown value"),
                        List.of(RUNTIME1_ACTIVITY1, RUNTIME1_ACTIVITY2),
                        System.currentTimeMillis()))
                .filter(
                    response -> {
                      var query = invocationOnMock.getArgument(0, ActiveExecutableQuery.class);
                      var providedType = query.type();
                      var elementId = query.elementId();
                      var processId = query.bpmnProcessId();
                      return switch (providedType) {
                        case null -> {
                          if (elementId != null && processId != null) {
                            yield response.elements().stream()
                                .anyMatch(
                                    element ->
                                        elementId.equals(element.element().elementId())
                                            && processId.equals(element.element().bpmnProcessId()));
                          } else {
                            yield true;
                          }
                        }
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

  Answer<Object> getInstance2Executables() {
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
                                new MessageCorrelationPoint.StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElementWithRuntimeData(
                                    "ProcessA", 1, 1, "elementIdProcessA", "tenantId"))),
                        Health.up(),
                        List.of(),
                        System.currentTimeMillis()),
                    new ActiveExecutableResponse(
                        RANDOM_ID_1,
                        TestWebhookExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of("inbound.context", "myPath", "inbound.type", TYPE_1),
                                new MessageCorrelationPoint.StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElementWithRuntimeData(
                                    "ProcessA", 1, 1, "elementId2ProcessA", "tenantId"))),
                        Health.down(new IllegalArgumentException("Test error message")),
                        List.of(),
                        System.currentTimeMillis()),
                    new ActiveExecutableResponse(
                        RANDOM_ID_2,
                        AnotherExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of(
                                    "inbound.other.prop", "myOtherValue", "inbound.type", TYPE_2),
                                new MessageCorrelationPoint.StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElementWithRuntimeData(
                                    "ProcessB", 2, 1, "", "tenantId"))),
                        Health.unknown("Test unknown key", "Test unknown value"),
                        List.of(
                            Activity.level(Severity.INFO).tag("runtime2").message("myMessage1")),
                        System.currentTimeMillis()),
                    new ActiveExecutableResponse(
                        RANDOM_ID_3,
                        AnotherExecutable.class,
                        List.of(
                            new InboundConnectorElement(
                                Map.of(
                                    "inbound.other.prop", "myOtherValue2", "inbound.type", TYPE_2),
                                new MessageCorrelationPoint.StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression", "=myPath", null),
                                new ProcessElementWithRuntimeData(
                                    "ProcessC", 2, 1, "id1", "tenantId")),
                            new InboundConnectorElement(
                                Map.of(
                                    "inbound.other.prop",
                                    "myOtherValue2_2",
                                    "inbound.type",
                                    TYPE_2),
                                new MessageCorrelationPoint.StandaloneMessageCorrelationPoint(
                                    "myPath", "=expression2", "=myPath2", null),
                                new ProcessElementWithRuntimeData(
                                    "ProcessC", 2, 1, "id2", "tenantId"))),
                        Health.up(),
                        List.of(RUNTIME2_ACTIVITY1, RUNTIME2_ACTIVITY2),
                        System.currentTimeMillis()))
                .filter(
                    response -> {
                      var query = invocationOnMock.getArgument(0, ActiveExecutableQuery.class);
                      var providedType = query.type();
                      var elementId = query.elementId();
                      var processId = query.bpmnProcessId();
                      return switch (providedType) {
                        case null -> {
                          if (elementId != null && processId != null) {
                            yield response.elements().stream()
                                .anyMatch(
                                    element ->
                                        elementId.equals(element.element().elementId())
                                            && processId.equals(element.element().bpmnProcessId()));
                          } else {
                            yield true;
                          }
                        }
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
}
