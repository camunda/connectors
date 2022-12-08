/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import java.util.Objects;

public class KafkaConnectorResponse {

  private String topic;
  private long timestamp;
  private long offset;
  private int partition;

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public long getOffset() {
    return offset;
  }

  public void setOffset(long offset) {
    this.offset = offset;
  }

  public int getPartition() {
    return partition;
  }

  public void setPartition(int partition) {
    this.partition = partition;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) return false;
    KafkaConnectorResponse that = (KafkaConnectorResponse) o;
    return timestamp == that.timestamp
        && offset == that.offset
        && partition == that.partition
        && topic.equals(that.topic);
  }

  @Override
  public int hashCode() {
    return Objects.hash(topic, timestamp, offset, partition);
  }

  @Override
  public String toString() {
    return "KafkaConnectorResponse{"
        + "topic='"
        + topic
        + '\''
        + ", timestamp="
        + timestamp
        + ", offset="
        + offset
        + ", partition="
        + partition
        + '}';
  }
}
