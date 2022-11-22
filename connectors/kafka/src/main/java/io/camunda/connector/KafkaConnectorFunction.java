/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.model.KafkaConnectorRequest;
import io.camunda.connector.model.KafkaConnectorResponse;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "KAFKA",
    inputVariables = {"authentication", "topic", "message", "additionalProperties"},
    type = "io.camunda:connector-kafka:1")
public class KafkaConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConnectorFunction.class);

  private final Function<Properties, Producer> producerCreatorFunction;

  public KafkaConnectorFunction() {
    this(KafkaProducer::new);
  }

  public KafkaConnectorFunction(final Function<Properties, Producer> producerCreatorFunction) {
    this.producerCreatorFunction = producerCreatorFunction;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws Exception {
    LOGGER.debug("Executing Kafka connector with context: " + context);
    var connectorRequest = context.getVariablesAsType(KafkaConnectorRequest.class);
    context.validate(connectorRequest);
    context.replaceSecrets(connectorRequest);
    return executeConnector(connectorRequest);
  }

  private KafkaConnectorResponse executeConnector(final KafkaConnectorRequest request)
      throws ExecutionException, InterruptedException, TimeoutException {
    Properties props = request.assembleKafkaClientProperties();
    Producer<String, String> producer = producerCreatorFunction.apply(props);
    Future<RecordMetadata> kafkaResponse =
        producer.send(
            new ProducerRecord<>(
                request.getTopic().getTopicName(),
                request.getMessage().getKey().toString(),
                request.getMessage().getValue().toString()));
    KafkaConnectorResponse result = new KafkaConnectorResponse();
    RecordMetadata recordMetadata = kafkaResponse.get(30, TimeUnit.SECONDS);
    result.setTopic(recordMetadata.topic());
    result.setPartition(recordMetadata.partition());
    result.setOffset(recordMetadata.offset());
    result.setTimestamp(recordMetadata.timestamp());
    return result;
  }
}
