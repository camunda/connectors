/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.QueueAttributeName;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.aws.AwsUtils;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.common.suppliers.AmazonSQSClientSupplier;
import io.camunda.connector.common.suppliers.DefaultAmazonSQSClientSupplier;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ConnectorElementType;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.inbound.model.SqsInboundProperties;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "AWS SQS Inbound", type = "io.camunda:aws-sqs-inbound:1")
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.AWSSQS.inbound.v1",
    name = "Amazon SQS Connector",
    icon = "icon.svg",
    version = 10,
    inputDataClass = SqsInboundProperties.class,
    description = "Receive messages from Amazon SQS.",
    metadata =
        @ElementTemplate.Metadata(
            keywords = {
              "receive message",
              "receive event",
              "receive message from queue",
              "receive event from queue"
            }),
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-sqs/?amazonsqs=inbound",
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "configuration", label = "Configuration"),
      @PropertyGroup(id = "queueProperties", label = "Queue properties"),
      @PropertyGroup(id = "messagePollingProperties", label = "Message polling properties"),
      @PropertyGroup(id = "input", label = "Use next attribute names for activation condition")
    },
    elementTypes = {
      @ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.MESSAGE_START_EVENT,
          templateIdOverride = "io.camunda.connectors.AWSSQS.startmessage.v1",
          templateNameOverride = "Amazon SQS Message Start Event Connector"),
      @ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateIdOverride = "io.camunda.connectors.AWSSQS.intermediate.v1",
          templateNameOverride = "Amazon SQS Intermediate Message Catch Event connector"),
      @ConnectorElementType(
          appliesTo = BpmnType.BOUNDARY_EVENT,
          elementType = BpmnType.BOUNDARY_EVENT,
          templateIdOverride = "io.camunda.connectors.AWSSQS.boundary.v1",
          templateNameOverride = "Amazon SQS Boundary Event Connector"),
      @ConnectorElementType(
          appliesTo = BpmnType.RECEIVE_TASK,
          elementType = BpmnType.RECEIVE_TASK,
          templateIdOverride = "io.camunda.connectors.AWSSQS.receive.v1",
          templateNameOverride = "Amazon SQS Receive Task Connector")
    })
public class SqsExecutable implements InboundConnectorExecutable<InboundConnectorContext> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqsExecutable.class);
  private final AmazonSQSClientSupplier sqsClientSupplier;
  private final ExecutorService executorService;
  private AmazonSQS amazonSQS;
  private SqsQueueConsumer sqsQueueConsumer;
  private InboundConnectorContext context;

  public SqsExecutable() {
    this.sqsClientSupplier = new DefaultAmazonSQSClientSupplier();
    this.executorService = Executors.newSingleThreadExecutor();
  }

  public SqsExecutable(
      final AmazonSQSClientSupplier sqsClientSupplier,
      final ExecutorService executorService,
      final SqsQueueConsumer sqsQueueConsumer) {
    this.sqsClientSupplier = sqsClientSupplier;
    this.executorService = executorService;
    this.sqsQueueConsumer = sqsQueueConsumer;
  }

  @Override
  public void activate(final InboundConnectorContext context) {
    this.context = context;
    LOGGER.info("Subscription activation requested by the Connector runtime");
    context.log(
        activity ->
            activity
                .withSeverity(Severity.INFO)
                .withTag("Subscription activation")
                .withMessage("Subscription activation requested"));
    SqsInboundProperties properties = context.bindProperties(SqsInboundProperties.class);

    var region =
        AwsUtils.extractRegionOrDefault(
            properties.getConfiguration(), properties.getQueue().region());
    amazonSQS =
        sqsClientSupplier.sqsClient(
            CredentialsProviderSupport.credentialsProvider(properties), region);

    try {
      amazonSQS.getQueueAttributes(
          properties.getQueue().url(),
          List.of(QueueAttributeName.ApproximateNumberOfMessages.toString()));
    } catch (QueueDoesNotExistException e) {
      LOGGER.error("Queue does not exist, failing subscription activation");
      throw new RuntimeException("Queue does not exist: " + properties.getQueue().url());
    }

    LOGGER.debug("SQS client created successfully");
    if (sqsQueueConsumer == null) {
      sqsQueueConsumer = new SqsQueueConsumer(amazonSQS, properties, context);
    }
    executorService.execute(sqsQueueConsumer);
    LOGGER.debug("SQS queue consumer started successfully");
    context.log(
        activity ->
            activity
                .withSeverity(Severity.INFO)
                .withTag("Subscription activation")
                .withMessage("Activated subscription for queue: " + properties.getQueue().url()));
    context.reportHealth(Health.up());
  }

  @Override
  public void deactivate() {
    sqsQueueConsumer.setQueueConsumerActive(false);
    context.reportHealth(Health.down());
    if (executorService != null) {
      LOGGER.debug("Shutting down executor service");
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
          LOGGER.debug("Executor service did not terminate gracefully, forcing shutdown");
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        LOGGER.debug(
            "Interrupted while waiting for executor service to terminate, forcing shutdown");
        executorService.shutdownNow();
      }
    }
    if (amazonSQS != null) {
      LOGGER.debug("Shutting down SQS client");
      amazonSQS.shutdown();
      LOGGER.debug("SQS client shut down successfully");
    }
  }
}
