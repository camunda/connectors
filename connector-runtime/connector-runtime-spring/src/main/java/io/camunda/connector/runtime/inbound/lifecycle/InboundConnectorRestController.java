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

import static io.camunda.connector.runtime.inbound.lifecycle.InboundConnectorManager.WEBHOOK_CONTEXT_BPMN_FIELD;

import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InboundConnectorRestController {

  private final InboundConnectorManager inboundManager;

  public InboundConnectorRestController(InboundConnectorManager inboundManager) {
    this.inboundManager = inboundManager;
  }

  @GetMapping("/inbound")
  public List<ActiveInboundConnectorResponse> getActiveInboundConnectors(
      @RequestParam(required = false) String bpmnProcessId,
      @RequestParam(required = false) String elementId,
      @RequestParam(required = false) String type) {
    var result =
        inboundManager.query(new ActiveInboundConnectorQuery(bpmnProcessId, elementId, type));
    return result.stream().map(this::mapToResponse).collect(Collectors.toList());
  }

  private ActiveInboundConnectorResponse mapToResponse(ActiveInboundConnector connector) {
    var properties = connector.properties();
    var health = connector.context().getHealth();
    Map<String, Object> details;
    if (connector.executable() instanceof WebhookConnectorExecutable) {
      details =
          new HashMap<>(Optional.ofNullable(health.getDetails()).orElse(Collections.emptyMap()));
      var path = Optional.ofNullable(properties.getProperties().get(WEBHOOK_CONTEXT_BPMN_FIELD));
      details.put("path", path.orElse(""));
    } else {
      details = health.getDetails();
    }
    return new ActiveInboundConnectorResponse(
        properties.getBpmnProcessId(),
        properties.getVersion(),
        properties.getElementId(),
        properties.getType(),
        details,
        health.getStatus());
  }
}
