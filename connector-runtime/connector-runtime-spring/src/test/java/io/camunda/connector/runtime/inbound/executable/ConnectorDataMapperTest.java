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

import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link ConnectorDataMapper}'s two-level gating of {@code inbound.context} rewriting:
 * the server-wide setting passed to the constructor, and the per-call override used by {@link
 * io.camunda.connector.runtime.instances.service.InboundInstancesService} to expose this as an
 * opt-in query parameter on its single-executable endpoint.
 */
class ConnectorDataMapperTest {

  private static final ExecutableId EXECUTABLE_ID = ExecutableId.fromDeduplicationId("dedup-id");

  private static ActiveExecutableResponse webhookResponse() {
    return new ActiveExecutableResponse(
        EXECUTABLE_ID,
        null, // executableClass absent, e.g. a failed activation
        List.of(
            new InboundConnectorElement(
                Map.of("inbound.context", "myPath", "inbound.type", "io.camunda:webhook:1"),
                new StandaloneMessageCorrelationPoint("myPath", "=expression", "=myPath", null),
                new ProcessElementWithRuntimeData(
                    "",
                    null,
                    null,
                    1,
                    1,
                    "",
                    null,
                    null,
                    "myTenant",
                    "myPhysicalTenant",
                    new ElementTemplateDetails("Test", "1", "icon"),
                    Map.of()))),
        Health.up(),
        Collections.emptyList(),
        System.currentTimeMillis());
  }

  @Test
  void serverFlagOff_perCallTrue_doesNotRewrite() {
    var mapper = new ConnectorDataMapper(false);

    var response = mapper.createActiveInboundConnectorResponse(webhookResponse(), true);

    assertThat(response.data().get("inbound.context")).isEqualTo("myPath");
  }

  @Test
  void serverFlagOn_perCallFalse_doesNotRewrite() {
    var mapper = new ConnectorDataMapper(true);

    var response = mapper.createActiveInboundConnectorResponse(webhookResponse(), false);

    assertThat(response.data().get("inbound.context")).isEqualTo("myPath");
  }

  @Test
  void serverFlagOn_perCallTrue_rewrites() {
    var mapper = new ConnectorDataMapper(true);

    var response = mapper.createActiveInboundConnectorResponse(webhookResponse(), true);

    assertThat(response.data().get("inbound.context"))
        .isEqualTo("myPhysicalTenant/myTenant/myPath");
  }

  @Test
  void singleArgOverload_behavesAsPerCallTrue() {
    var mapper = new ConnectorDataMapper(true);

    var response = mapper.createActiveInboundConnectorResponse(webhookResponse());

    assertThat(response.data().get("inbound.context"))
        .isEqualTo("myPhysicalTenant/myTenant/myPath");
  }
}
