/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.WebhookConnectorExecutable;
import io.camunda.connector.impl.inbound.WebhookRequestPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "WEBHOOK_INBOUND", type = "io.camunda:webhook:1")
public class HttpWebhookExecutable implements WebhookConnectorExecutable {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpWebhookExecutable.class);

  public HttpWebhookExecutable() {}

  @Override
  public Object triggerWebhook(InboundConnectorContext context, WebhookRequestPayload webhookRequestPayload) {
    LOGGER.error("IGPETROV: webhook triggered");
    LOGGER.error("IGPETROV: got lovely payload: " + webhookRequestPayload);
    return "OK";
  }

  @Override
  public void activate(InboundConnectorContext inboundConnectorContext) {
    LOGGER.error("IGPETROV: webhook activated");
  }

  @Override
  public void deactivate() {
    LOGGER.error("IGPETROV: webhook deactivate");
  }
}
