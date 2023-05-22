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
package io.camunda.connector.runtime.inbound.webhook;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.WebhookConnectorExecutable;
import io.camunda.connector.impl.inbound.WebhookRequestPayload;
import io.camunda.connector.runtime.inbound.webhook.model.HttpServletRequestWebhookRequestPayload;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static io.camunda.zeebe.spring.client.metrics.MetricsRecorder.ACTION_ACTIVATED;
import static io.camunda.zeebe.spring.client.metrics.MetricsRecorder.ACTION_COMPLETED;
import static io.camunda.zeebe.spring.client.metrics.MetricsRecorder.METRIC_NAME_INBOUND_CONNECTOR;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

@RestController
@ConditionalOnProperty("camunda.connector.webhook.enabled")
public class InboundWebhookRestController {

  private static final Logger LOG = LoggerFactory.getLogger(InboundWebhookRestController.class);
  
  protected static final String METRIC_WEBHOOK_VALUE = "WEBHOOK";
  private final WebhookConnectorRegistry webhookConnectorRegistry;
  private final MetricsRecorder metricsRecorder;

  @Autowired
  public InboundWebhookRestController(
      final WebhookConnectorRegistry webhookConnectorRegistry,
      final MetricsRecorder metricsRecorder) {
    this.webhookConnectorRegistry = webhookConnectorRegistry;
    this.metricsRecorder = metricsRecorder;
  }
  
  @RequestMapping(method = {GET, POST, PUT, DELETE}, path = "/inbound/{context}")
  public ResponseEntity<WebhookResponse> inbound(
      @PathVariable String context,
      @RequestHeader Map<String, String> headers,
      @RequestBody(required = false) byte[] bodyAsByteArray,
      @RequestParam Map<String,String> params,
      HttpServletRequest httpServletRequest)
      throws IOException {

    LOG.trace("Received inbound hook on {}", context);

    if (!webhookConnectorRegistry.containsContextPath(context)) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND.value(), "No webhook found for context: " + context, null);
    }
    incrementMetric(ACTION_ACTIVATED);
    
    WebhookRequestPayload payload = 
            new HttpServletRequestWebhookRequestPayload(httpServletRequest, params, headers, bodyAsByteArray);

    WebhookResponse response = new WebhookResponse();
    Collection<InboundConnectorContext> connectors =
        webhookConnectorRegistry.getWebhookConnectorByContextPath(context);
    
    for (InboundConnectorContext connectorContext : connectors) {
      WebhookConnectorExecutable executable = 
              webhookConnectorRegistry.getByType(connectorContext.getProperties().getType());
      WebhookConnectorProperties connectorProperties = 
              new WebhookConnectorProperties(connectorContext.getProperties());
      try {
        var webhookResult = executable.triggerWebhook(connectorContext, payload);
        
        if (webhookResult.executionResult() != null && webhookResult.executionResult().isActivated()) {
          response.setWebhookData(webhookResult.body());
          response.addExecutedConnector(connectorProperties, webhookResult.executionResult());
        } else {
          response.addUnactivatedConnector(connectorProperties);
        }
        
      } catch (Exception e) {
        response.addException(connectorProperties, e);
        incrementMetric(METRIC_NAME_INBOUND_CONNECTOR);
      }
    }

    incrementMetric(ACTION_COMPLETED);
    return ResponseEntity.ok(response);
  }
  
  private void incrementMetric(final String action) {
    metricsRecorder.increase(METRIC_NAME_INBOUND_CONNECTOR, action, METRIC_WEBHOOK_VALUE);
  }
  
}
