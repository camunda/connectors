/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import com.amazonaws.services.sqs.AmazonSQS;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.common.suppliers.AmazonSQSClientSupplier;
import io.camunda.connector.common.suppliers.DefaultAmazonSQSClientSupplier;
import io.camunda.connector.inbound.model.SqsInboundProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "AWSSQS_INBOUND", type = "io.camunda:connector-aws-sqs-inbound:1")
public class SqsExecutable implements InboundConnectorExecutable {
  private static final Logger LOGGER = LoggerFactory.getLogger(SqsExecutable.class);

  private final AmazonSQSClientSupplier sqsClientSupplier;
  private AmazonSQS amazonSQS;

  public SqsExecutable() {
    this.sqsClientSupplier = new DefaultAmazonSQSClientSupplier();
  }

  public SqsExecutable(final AmazonSQSClientSupplier sqsClientSupplier) {
    this.sqsClientSupplier = sqsClientSupplier;
  }

  @Override
  public void activate(final InboundConnectorContext context) {
    SqsInboundProperties properties = context.getPropertiesAsType(SqsInboundProperties.class);

    LOGGER.info("Subscription activation requested by the Connector runtime: {}", properties);

    context.replaceSecrets(properties);
    context.validate(properties);

    amazonSQS =
        sqsClientSupplier.sqsClient(
            properties.getAuthentication().getAccessKey(),
            properties.getAuthentication().getSecretKey(),
            properties.getQueue().getRegion());

    SqsQueueConsumer sqsQueueConsumer = new SqsQueueConsumer(amazonSQS, properties, context);

    sqsQueueConsumer.consumeQueueUntilActivated();
  }

  @Override
  public void deactivate() {
    if (amazonSQS != null) {
      amazonSQS.shutdown();
    }
  }
}
