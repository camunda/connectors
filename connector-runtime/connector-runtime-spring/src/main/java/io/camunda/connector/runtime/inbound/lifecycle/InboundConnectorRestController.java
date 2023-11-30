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
package io.camunda.connector.runtime.inbound.lifecycle;

import io.camunda.connector.api.inbound.ActivityLog;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.runtime.core.inbound.InboundConnectorReportingContext;
import io.camunda.connector.runtime.inbound.webhook.model.CommonWebhookProperties;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InboundConnectorRestController {
  private static final Logger LOG = LoggerFactory.getLogger(InboundConnectorRestController.class);

  private final InboundConnectorManager inboundManager;

  public InboundConnectorRestController(InboundConnectorManager inboundManager) {
    this.inboundManager = inboundManager;
  }

  @GetMapping("/inbound")
  public List<ActiveInboundConnectorResponse> getActiveInboundConnectors(
      @RequestParam(required = false) String bpmnProcessId,
      @RequestParam(required = false) String elementId,
      @RequestParam(required = false) String type) {
    return getActiveInboundConnectors(bpmnProcessId, elementId, type, null);
  }

  @GetMapping("/tenants/{tenantId}/inbound")
  public List<ActiveInboundConnectorResponse> getActiveInboundConnectorsForTenantId(
      @PathVariable String tenantId,
      @RequestParam(required = false) String bpmnProcessId,
      @RequestParam(required = false) String elementId,
      @RequestParam(required = false) String type) {
    return getActiveInboundConnectors(bpmnProcessId, elementId, type, tenantId);
  }

  @GetMapping("/tenants/{tenantId}/inbound/{bpmnProcessId}/{elementId}/logs")
  public List<Queue<ActivityLog>> getActiveInboundConnectorLogs(
      @PathVariable String tenantId,
      @PathVariable String bpmnProcessId,
      @PathVariable String elementId) {
    var result =
        inboundManager.query(
            new ActiveInboundConnectorQuery(bpmnProcessId, elementId, null, tenantId));
    return result.stream()
        .map(connector -> ((InboundConnectorReportingContext) connector.context()).getLogs())
        .collect(Collectors.toList());
  }

  private List<ActiveInboundConnectorResponse> getActiveInboundConnectors(
      String bpmnProcessId, String elementId, String type, String tenantId) {
    var result =
        inboundManager.query(
            new ActiveInboundConnectorQuery(bpmnProcessId, elementId, type, tenantId));
    return result.stream().map(this::mapToInboundResponse).collect(Collectors.toList());
  }

  private Map<String, Object> getData(ActiveInboundConnector connector) {
    Map<String, Object> data = Map.of();
    if (connector.executable() instanceof WebhookConnectorExecutable) {
      try {
        var castedProps = connector.context().bindProperties(CommonWebhookProperties.class);
        data = Map.of("path", castedProps.getContext());
      } catch (Exception e) {
        LOG.error("ERROR: webhook connector doesn't have context path property", e);
      }
    }
    return data;
  }

  private ActiveInboundConnectorResponse mapToInboundResponse(ActiveInboundConnector connector) {
    var definition = connector.context().getDefinition();
    var health = ((InboundConnectorReportingContext) connector.context()).getHealth();
    return new ActiveInboundConnectorResponse(
        definition.bpmnProcessId(),
        definition.version(),
        definition.elementId(),
        definition.type(),
        definition.tenantId(),
        (connector.executable() instanceof WebhookConnectorExecutable) ? "webhook" : "",
        getData(connector),
        health);
  }
}
