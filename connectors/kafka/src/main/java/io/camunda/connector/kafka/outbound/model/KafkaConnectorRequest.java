/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

public class KafkaConnectorRequest {

  protected static final String SESSION_TIMEOUT_MS_RECOMMENDED_VALUE = "45000";
  protected static final String DEFAULT_API_TIMEOUT_MS = "60000";
  protected static final String HEARTBEAT_INTERVAL_MS = "3000";
  protected static final String DELIVERY_TIMEOUT_MS_RECOMMENDED_VALUE = "45000";
  protected static final String LINGER_MS = "0";
  protected static final String REQUEST_TIMEOUT_MS = "30000";
  protected static final String MAX_BLOCK_MS_RECOMMENDED_VALUE = "60000";
  protected static final String CLIENT_DNS_LOOKUP_RECOMMENDED_VALUE = "use_all_dns_ips";
  protected static final String ACKS_RECOMMENDED_VALUE = "all";

  @Valid private KafkaAuthentication authentication;
  @Valid @NotNull private KafkaTopic topic;
  @Valid @NotNull private KafkaMessage message;

  private Map<String, Object> additionalProperties = new HashMap<>();

  public KafkaAuthentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(KafkaAuthentication authentication) {
    this.authentication = authentication;
  }

  public KafkaTopic getTopic() {
    return topic;
  }

  public void setTopic(KafkaTopic topic) {
    this.topic = topic;
  }

  public KafkaMessage getMessage() {
    return message;
  }

  public void setMessage(KafkaMessage message) {
    this.message = message;
  }

  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  public void setAdditionalProperties(Map<String, Object> additionalProperties) {
    this.additionalProperties = additionalProperties;
  }

  // Kafka client is built using java.utils.Properties.
  // This method creates properties required to establish connection and produce messages.
  public Properties assembleKafkaClientProperties() {
    Properties props = new Properties();

    // Step 1: collect properties directly from the form
    if (authentication != null) {
      Properties authProps = this.authentication.produceAuthenticationProperties();
      props.putAll(authProps);
    }

    Properties topicProps = this.topic.produceTopicProperties();
    props.putAll(topicProps);

    if (this.message != null) { // can be valid in case of inbound
      Properties messageProps = this.message.produceMessageProperties();
      props.putAll(messageProps);
    }

    // Step 2: set default recommended properties
    // See the list of possible values at org.apache.kafka.clients.producer.ProducerConfig
    props.put(CommonClientConfigs.SESSION_TIMEOUT_MS_CONFIG, SESSION_TIMEOUT_MS_RECOMMENDED_VALUE);
    props.put(ProducerConfig.CLIENT_DNS_LOOKUP_CONFIG, CLIENT_DNS_LOOKUP_RECOMMENDED_VALUE);
    props.put(ProducerConfig.ACKS_CONFIG, ACKS_RECOMMENDED_VALUE);
    props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DELIVERY_TIMEOUT_MS_RECOMMENDED_VALUE);
    props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS_RECOMMENDED_VALUE);
    props.put(ProducerConfig.LINGER_MS_CONFIG, LINGER_MS);
    props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MS);
    props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, HEARTBEAT_INTERVAL_MS);
    props.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, DEFAULT_API_TIMEOUT_MS);

    // Step 3: override properties provided by the client
    if (!additionalProperties.isEmpty()) {
      props.putAll(additionalProperties);
    }

    return props;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KafkaConnectorRequest that = (KafkaConnectorRequest) o;
    return authentication.equals(that.authentication)
        && topic.equals(that.topic)
        && message.equals(that.message)
        && Objects.equals(additionalProperties, that.additionalProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, topic, message, additionalProperties);
  }

  @Override
  public String toString() {
    return "KafkaConnectorRequest{"
        + "auth="
        + authentication
        + ", topic="
        + topic
        + ", message="
        + message
        + ", additionalProperties="
        + additionalProperties
        + '}';
  }
}
