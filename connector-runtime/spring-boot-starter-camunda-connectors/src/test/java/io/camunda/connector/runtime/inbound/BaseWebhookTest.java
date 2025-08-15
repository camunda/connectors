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

import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import java.util.List;
import java.util.Map;

public class BaseWebhookTest {
  public static InboundConnectorDetails.ValidInboundConnectorDetails webhookDefinition(
      String bpmnProcessId, int version, String path) {
    var details =
        InboundConnectorDetails.of(
            bpmnProcessId + version + path,
            List.of(
                webhookElement(
                    (bpmnProcessId + version).hashCode(), bpmnProcessId, version, path)));
    assertThat(details).isInstanceOf(InboundConnectorDetails.ValidInboundConnectorDetails.class);
    return (InboundConnectorDetails.ValidInboundConnectorDetails) details;
  }

  private static InboundConnectorElement webhookElement(
      long processDefinitionKey, String bpmnProcessId, int version, String path) {

    return new InboundConnectorElement(
        Map.of("inbound.type", "io.camunda:webhook:1", "inbound.context", path),
        new StartEventCorrelationPoint(bpmnProcessId, version, processDefinitionKey),
        new ProcessElementWithRuntimeData(
            bpmnProcessId, version, processDefinitionKey, "testElement", "<default>"));
  }
}
