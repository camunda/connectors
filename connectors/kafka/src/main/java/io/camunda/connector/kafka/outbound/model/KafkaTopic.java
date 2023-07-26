/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import jakarta.validation.constraints.NotEmpty;
import java.util.Objects;
import java.util.Properties;
import org.apache.kafka.clients.producer.ProducerConfig;

public class KafkaTopic {

  @NotEmpty private String bootstrapServers;
  @NotEmpty private String topicName;

  public KafkaTopic() {}

  public KafkaTopic(String bootstrapServers, String topicName) {
    this.bootstrapServers = bootstrapServers;
    this.topicName = topicName;
  }

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public void setBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  public String getTopicName() {
    return topicName;
  }

  public void setTopicName(String topicName) {
    this.topicName = topicName;
  }

  public Properties produceTopicProperties() {
    Properties topicProps = new Properties();
    topicProps.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    return topicProps;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KafkaTopic that = (KafkaTopic) o;
    return bootstrapServers.equals(that.bootstrapServers) && topicName.equals(that.topicName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bootstrapServers, topicName);
  }

  @Override
  public String toString() {
    return "KafkaTopic{"
        + "bootstrapServers='"
        + bootstrapServers
        + '\''
        + ", topicName='"
        + topicName
        + '\''
        + '}';
  }
}
