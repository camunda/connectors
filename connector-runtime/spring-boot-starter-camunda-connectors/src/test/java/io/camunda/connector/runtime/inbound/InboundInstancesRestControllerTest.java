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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableQuery;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableResponse;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestConnectorRuntimeApplication.class)
@AutoConfigureMockMvc
class InboundInstancesRestControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InboundExecutableRegistry executableRegistry;

  private static final String TYPE_1 = "webhook";

  private static final ExecutableId RANDOM_ID_1 = ExecutableId.fromDeduplicationId("theid1");
  private static final ExecutableId RANDOM_ID_2 = ExecutableId.fromDeduplicationId("theid2");
  private static final ExecutableId RANDOM_ID_3 = ExecutableId.fromDeduplicationId("theid3");
  private static final String TYPE_2 = "anotherType";

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

  @BeforeEach
  public void init() {
    when(executableRegistry.getConnectorName(TYPE_1)).thenReturn("Webhook");
    when(executableRegistry.getConnectorName(TYPE_2)).thenReturn("AnotherType");
    when(executableRegistry.query(any()))
        .thenAnswer(
            invocationOnMock ->
                "UNKNOWN-ID"
                        .equals(invocationOnMock.getArgument(0, ActiveExecutableQuery.class).type())
                    ? Collections.emptyList()
                    : Stream.of(
                            new ActiveExecutableResponse(
                                RANDOM_ID_1,
                                TestWebhookExecutable.class,
                                List.of(
                                    new InboundConnectorElement(
                                        Map.of("inbound.context", "myPath", "inbound.type", TYPE_1),
                                        new StandaloneMessageCorrelationPoint(
                                            "myPath", "=expression", "=myPath", null),
                                        new ProcessElementWithRuntimeData(
                                            "ProcessA", 1, 1, "", ""))),
                                Health.up(),
                                Collections.emptyList(),
                                System.currentTimeMillis()),
                            new ActiveExecutableResponse(
                                RANDOM_ID_2,
                                AnotherExecutable.class,
                                List.of(
                                    new InboundConnectorElement(
                                        Map.of(
                                            "inbound.other.prop",
                                            "myOtherValue",
                                            "inbound.type",
                                            TYPE_2),
                                        new StandaloneMessageCorrelationPoint(
                                            "myPath", "=expression", "=myPath", null),
                                        new ProcessElementWithRuntimeData(
                                            "ProcessB", 2, 1, "", ""))),
                                Health.up(),
                                Collections.emptyList(),
                                System.currentTimeMillis()),
                            new ActiveExecutableResponse(
                                RANDOM_ID_3,
                                AnotherExecutable.class,
                                List.of(
                                    new InboundConnectorElement(
                                        Map.of(
                                            "inbound.other.prop",
                                            "myOtherValue2",
                                            "inbound.type",
                                            TYPE_2),
                                        new StandaloneMessageCorrelationPoint(
                                            "myPath", "=expression", "=myPath", null),
                                        new ProcessElementWithRuntimeData(
                                            "ProcessC", 2, 1, "id1", "")),
                                    new InboundConnectorElement(
                                        Map.of(
                                            "inbound.other.prop",
                                            "myOtherValue2_2",
                                            "inbound.type",
                                            TYPE_2),
                                        new StandaloneMessageCorrelationPoint(
                                            "myPath", "=expression2", "=myPath2", null),
                                        new ProcessElementWithRuntimeData(
                                            "ProcessC", 2, 1, "id2", ""))),
                                Health.up(),
                                List.of(
                                    Activity.level(Severity.INFO).tag("myTag").message("myMessage"),
                                    Activity.level(Severity.ERROR)
                                        .tag("myTag2")
                                        .message("myMessage2")),
                                System.currentTimeMillis()))
                        .filter(
                            response -> {
                              var providedType =
                                  invocationOnMock
                                      .getArgument(0, ActiveExecutableQuery.class)
                                      .type();
                              return switch (providedType) {
                                case null -> true;
                                case TYPE_1 -> response.executableId().equals(RANDOM_ID_1);
                                case TYPE_2 ->
                                    response.executableId().equals(RANDOM_ID_2)
                                        || response.executableId().equals(RANDOM_ID_3);
                                default -> false;
                              };
                            })
                        .toList());
  }

  @Test
  public void shouldReturnConnectorInstances() throws Exception {
    var response =
        mockMvc
            .perform(get("/inbound-instances"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<ConnectorInstances> instance =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});
    assertEquals(2, instance.size());
    var instance1 = instance.get(0);
    assertEquals(TYPE_1, instance1.connectorId());
    assertEquals("Webhook", instance1.connectorName());
    assertEquals(1, instance1.instances().size());
    assertEquals(RANDOM_ID_1, instance1.instances().get(0).executableId());
    assertEquals("ProcessA", instance1.instances().get(0).elements().getFirst().bpmnProcessId());

    var instance2 = instance.get(1);
    assertEquals(TYPE_2, instance2.connectorId());
    assertEquals("AnotherType", instance2.connectorName());
    assertEquals(2, instance2.instances().size());
    assertEquals(RANDOM_ID_2, instance2.instances().get(0).executableId());
    assertEquals("ProcessB", instance2.instances().get(0).elements().getFirst().bpmnProcessId());
    assertEquals(RANDOM_ID_3, instance2.instances().get(1).executableId());
    assertEquals("ProcessC", instance2.instances().get(1).elements().getFirst().bpmnProcessId());
  }

  @Test
  public void shouldReturn404_whenUnknownConnectorType() throws Exception {
    mockMvc
        .perform(get("/inbound-instances/UNKNOWN-ID"))
        .andExpect(status().isNotFound())
        .andExpect(
            content()
                .string(
                    containsString(
                        "Data of type 'ConnectorInstances' with id 'UNKNOWN-ID' not found")));
  }

  @Test
  public void shouldReturnSingleConnectorInstance() throws Exception {
    var response =
        mockMvc
            .perform(get("/inbound-instances/" + TYPE_1))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ConnectorInstances instance =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, ConnectorInstances.class);
    assertEquals(TYPE_1, instance.connectorId());
    assertEquals("Webhook", instance.connectorName());
    assertEquals(1, instance.instances().size());
    assertEquals(RANDOM_ID_1, instance.instances().get(0).executableId());
    assertEquals("ProcessA", instance.instances().get(0).elements().getFirst().bpmnProcessId());
  }

  @Test
  public void shouldReturn404_whenUnknownExecutableId() throws Exception {
    mockMvc
        .perform(get("/inbound-instances/" + TYPE_1 + "/executables/UNKNOWN-ID"))
        .andExpect(status().isNotFound())
        .andExpect(
            content()
                .string(
                    containsString(
                        "Data of type 'ActiveInboundConnectorResponse' with id 'UNKNOWN-ID' not found")));
  }

  @Test
  public void shouldReturn404_whenUnknownConnectorTypeAndValidExecutableId() throws Exception {
    mockMvc
        .perform(get("/inbound-instances/UNKNOWN-ID/executables/" + RANDOM_ID_1.getId()))
        .andExpect(status().isNotFound())
        .andExpect(
            content()
                .string(
                    containsString(
                        "Data of type 'ConnectorInstances' with id 'UNKNOWN-ID' not found")));
  }

  @Test
  public void shouldReturnSingleExecutable() throws Exception {
    var response =
        mockMvc
            .perform(get("/inbound-instances/" + TYPE_1 + "/executables/" + RANDOM_ID_1.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ActiveInboundConnectorResponse executable =
        ConnectorsObjectMapperSupplier.getCopy()
            .readValue(response, ActiveInboundConnectorResponse.class);
    assertEquals(RANDOM_ID_1, executable.executableId());
    assertEquals("ProcessA", executable.elements().getFirst().bpmnProcessId());
  }

  @Test
  public void shouldReturnEmptyActivityLogs_whenNoLogs() throws Exception {
    var response =
        mockMvc
            .perform(
                get(
                    "/inbound-instances/"
                        + TYPE_1
                        + "/executables/"
                        + RANDOM_ID_1.getId()
                        + "/logs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Activity> logs =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});
    assertTrue(logs.isEmpty());
  }

  @Test
  public void shouldReturnActivityLogs_whenTypeProvided() throws Exception {
    var response =
        mockMvc
            .perform(
                get(
                    "/inbound-instances/"
                        + TYPE_2
                        + "/executables/"
                        + RANDOM_ID_3.getId()
                        + "/logs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Activity> logs =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});
    assertEquals(2, logs.size());
    var log1 = logs.stream().filter(a -> a.severity() == Severity.INFO).findFirst().get();
    assertEquals("myTag", log1.tag());
    assertEquals("myMessage", log1.message());
    var log2 = logs.stream().filter(a -> a.severity() == Severity.ERROR).findFirst().get();
    assertEquals("myTag2", log2.tag());
    assertEquals("myMessage2", log2.message());
  }
}
