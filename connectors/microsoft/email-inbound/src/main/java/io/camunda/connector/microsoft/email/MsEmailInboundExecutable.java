/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.microsoft.email.model.config.MsInboundEmailProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(
    name = "Microsoft O365 Email Consumer",
    type = "io.camunda:connector-o365-email-inbound:1")
@ElementTemplate(
    engineVersion = "^8.9",
    id = "io.camunda.connectors.MSFT.O365.Mail.inbound",
    name = "Microsoft O365 Inbound Email Connector",
    icon = "icon.svg",
    version = 1,
    inputDataClass = MsInboundEmailProperties.class,
    description = "Poll M365 Outlook emails",
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/microsoft-o365-mail-inbound/",
    metadata = @ElementTemplate.Metadata(keywords = {"email", "Office365", "Outlook"}),
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "pollingConfig", label = "Listener Information"),
      @ElementTemplate.PropertyGroup(id = "postprocessing", label = "Postprocessing")
    })
public class MsEmailInboundExecutable
    implements InboundConnectorExecutable<InboundConnectorContext> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MsEmailInboundExecutable.class);

  private EmailPollingWorker worker;
  private InboundConnectorContext context;

  @Override
  public void activate(InboundConnectorContext context) {
    worker = new EmailPollingWorker(context);
    this.context = context;
  }

  @Override
  public void deactivate() throws Exception {
    worker.shutdown();
    context.reportHealth(Health.down());
    Thread.sleep(Duration.ofMillis(800));
    if (!worker.isShutdown()) {
      LOGGER.debug("Executor service did not terminate gracefully, forcing shutdown");
      worker.forceShutdown();
    }
  }
}
