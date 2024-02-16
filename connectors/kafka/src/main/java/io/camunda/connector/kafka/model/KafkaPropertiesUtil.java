/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import java.util.Properties;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;

public final class KafkaPropertiesUtil {

  private static final String SESSION_TIMEOUT_MS_RECOMMENDED_VALUE = "45000";
  private static final String DEFAULT_API_TIMEOUT_MS = "60000";
  private static final String HEARTBEAT_INTERVAL_MS = "3000";
  private static final String DELIVERY_TIMEOUT_MS_RECOMMENDED_VALUE = "45000";
  private static final String LINGER_MS = "0";
  private static final String REQUEST_TIMEOUT_MS = "30000";
  private static final String MAX_BLOCK_MS_RECOMMENDED_VALUE = "60000";
  private static final String CLIENT_DNS_LOOKUP_RECOMMENDED_VALUE = "use_all_dns_ips";
  private static final String ACKS_RECOMMENDED_VALUE = "all";

  private static final String SASL_JAAS_CONFIG_VALUE =
      "org.apache.kafka.common.security.plain.PlainLoginModule   required username='%s'   password='%s';";

  private static final String SECURITY_PROTOCOL_VALUE = "SASL_SSL"; // default value
  private static final String SASL_MECHANISM_VALUE = "PLAIN"; // default value

  private KafkaPropertiesUtil() {}

  private static final String STRING_SERIALIZER =
      "org.apache.kafka.common.serialization.StringSerializer";
  private static final String BYTE_ARRAY_SERIALIZER =
      "org.apache.kafka.common.serialization.ByteArraySerializer";

  // Kafka client is built using java.utils.Properties.
  // This method creates properties required to establish connection and produce messages.
  public static Properties assembleKafkaClientProperties(KafkaConnectorRequest request) {
    Properties props = new Properties();

    // Step 1: collect properties directly from the form
    if (request.authentication() != null) {
      Properties authProps =
          KafkaPropertiesUtil.produceAuthenticationProperties(request.authentication());
      props.putAll(authProps);
    }
    Properties topicProps = new Properties();
    topicProps.setProperty(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, request.topic().bootstrapServers());
    props.putAll(topicProps);

    if (request.message() != null) { // can be valid in case of inbound
      Properties messageProps = new Properties();
      messageProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, STRING_SERIALIZER);
      if (request.avro() == null) {
        messageProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, STRING_SERIALIZER);
      } else {
        messageProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, BYTE_ARRAY_SERIALIZER);
      }
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
    if (request.additionalProperties() != null && !request.additionalProperties().isEmpty()) {
      props.putAll(request.additionalProperties());
    }

    return props;
  }

  public static Properties produceAuthenticationProperties(KafkaAuthentication authentication) {
    Properties authProps = new Properties();

    // Both username and password arrived empty thus not setting security config.
    if ((authentication.username() == null || authentication.username().isBlank())
        && (authentication.password() == null || authentication.password().isBlank())) {
      return authProps;
    }

    if (authentication.username() != null
        && !authentication.username().isBlank()
        && authentication.password() != null
        && !authentication.password().isBlank()) {
      authProps.put(
          SaslConfigs.SASL_JAAS_CONFIG,
          String.format(
              SASL_JAAS_CONFIG_VALUE, authentication.username(), authentication.password()));
      authProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SECURITY_PROTOCOL_VALUE);
      authProps.put(SaslConfigs.SASL_MECHANISM, SASL_MECHANISM_VALUE);
    } else {
      throw new ConnectorInputException(
          new RuntimeException("Username / password pair is required"));
    }
    return authProps;
  }
}
