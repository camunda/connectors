/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.inbound;

import io.camunda.connector.api.error.ConnectorInputException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener that is invoked when partitions are assigned to a consumer. It is used to update the
 * offsets of the partitions. Only used when the connector is configured with a list of offsets to
 * start from.
 *
 * @param topicName The topic name
 * @param consumer The consumer
 * @param offsets The offsets to start from, one for each partition
 */
public record OffsetUpdateRequiredListener(
    String topicName, Consumer<Object, Object> consumer, List<Long> offsets)
    implements ConsumerRebalanceListener {

  private static final Logger LOG = LoggerFactory.getLogger(OffsetUpdateRequiredListener.class);

  @Override
  public void onPartitionsRevoked(Collection<TopicPartition> partitions) {}

  @Override
  public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
    LOG.debug(
        "Partitions assigned: {} for topic: {} and consumer: {}",
        partitions,
        topicName,
        consumer.groupMetadata().memberId());
    Optional.ofNullable(offsets)
        .filter(listOffsets -> !listOffsets.isEmpty())
        .ifPresent(offsets -> seekOffsets(consumer, partitions, offsets, topicName));
  }

  private void seekOffsets(
      Consumer<Object, Object> consumer,
      Collection<TopicPartition> partitions,
      List<Long> offsets,
      String topicName) {
    if (consumer.partitionsFor(topicName).size() != offsets.size()) {
      throw new ConnectorInputException(
          new IllegalArgumentException(
              "Number of offsets provided is not equal the number of partitions!"));
    }
    partitions.forEach(partition -> setPartitionOffset(partition, consumer, offsets));
  }

  private void setPartitionOffset(
      TopicPartition partition, Consumer<Object, Object> consumer, List<Long> offsets) {
    Long offset = offsets.get(partition.partition());
    if (offset != null) {
      LOG.debug(
          "Overriding partition {} to offset: {} for consumer: {}",
          partition,
          offset,
          consumer.groupMetadata().memberId());
      consumer.seek(partition, offset);
    }
  }
}
