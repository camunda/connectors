/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.ActivityLogTag;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.microsoft.email.model.config.MsInboundEmailProperties;
import java.util.concurrent.TimeUnit;
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
    },
    elementTypes = {
      @ElementTemplate.ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.MESSAGE_START_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.MSFT.O365.EmailMessageStart.v1",
          templateNameOverride = "Microsoft O365 Email Message Start Event Connector"),
      @ElementTemplate.ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.MSFT.O365.EmailIntermediate.v1",
          templateNameOverride = "Microsoft O365 Email Intermediate Catch Event Connector"),
      @ElementTemplate.ConnectorElementType(
          appliesTo = BpmnType.BOUNDARY_EVENT,
          elementType = BpmnType.BOUNDARY_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.MSFT.O365.EmailBoundary.v1",
          templateNameOverride = "Microsoft O365 Email Boundary Event Connector")
    })
public class MsEmailInboundExecutable
    implements InboundConnectorExecutable<InboundConnectorContext> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MsEmailInboundExecutable.class);

  private EmailPollingWorker worker;
  private InboundConnectorContext context;

  @Override
  public void activate(InboundConnectorContext context) {
    this.context = context;
    context.log(
        activity ->
            activity
                .withSeverity(Severity.INFO)
                .withTag(ActivityLogTag.CONSUMER)
                .withMessage(
                    "Microsoft O365 Email connector activation requested for process "
                        + context.getDefinition().elements().stream()
                            .map(ProcessElement::bpmnProcessId)
                            .toList()));
    try {
      worker = new EmailPollingWorker(context);
      context.reportHealth(Health.up());
      context.log(
          activity ->
              activity
                  .withSeverity(Severity.INFO)
                  .withTag(ActivityLogTag.CONSUMER)
                  .withMessage("Microsoft O365 Email connector activated successfully"));
    } catch (Exception e) {
      LOGGER.error("Failed to activate Microsoft O365 Email Inbound connector", e);
      context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag(ActivityLogTag.CONSUMER)
                  .withMessage(
                      "Microsoft O365 Email connector activation failed: " + e.getMessage()));
      context.reportHealth(Health.down(e));
      throw e;
    }
  }

  @Override
  public void deactivate() throws Exception {
    context.log(
        activity ->
            activity
                .withSeverity(Severity.INFO)
                .withTag(ActivityLogTag.CONSUMER)
                .withMessage("Microsoft O365 Email connector deactivation requested"));
    worker.shutdown();
    context.reportHealth(Health.down());
    try {
      if (!worker.awaitTermination(800, TimeUnit.MILLISECONDS)) {
        LOGGER.debug("Executor service did not terminate gracefully, forcing shutdown");
        worker.forceShutdown();
      }
    } catch (InterruptedException e) {
      LOGGER.debug("Interrupted while waiting for executor service to terminate, forcing shutdown");
      Thread.currentThread().interrupt();
      worker.forceShutdown();
    }
  }
}
