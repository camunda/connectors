/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.scala.DefaultScalaModule$;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaPropertyTransformer {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaPropertyTransformer.class);

  private static final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(DefaultScalaModule$.MODULE$)
          .registerModule(new JavaTimeModule())
          // deserialize unknown types as empty objects
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);

  static final String DEFAULT_GROUP_ID_PREFIX = "kafka-inbound-connector";

  protected static final String DEFAULT_KEY_DESERIALIZER =
      "org.apache.kafka.common.serialization.StringDeserializer";

  public static List<Long> getOffsets(Object offsets) {
    if (offsets == null) {
      return null;
    }
    List<Long> offsetCollection = null;
    if (offsets instanceof Collection<?>) {
      offsetCollection = (List<Long>) offsets;
    } else if (offsets instanceof String) {
      offsetCollection = convertStringToList((String) offsets);
    } else {
      // We accept only List or String input for offsets
      throw new IllegalArgumentException(
          "Invalid input type for offsets. Supported types are: List<Long> and String. Got "
              + offsets.getClass()
              + " instead.");
    }
    return offsetCollection;
  }

  public static List<Long> convertStringToList(String string) {
    if (StringUtils.isBlank(string)) {
      return new ArrayList<>();
    }
    return Arrays.stream(string.split(","))
        .map(s -> Long.parseLong(s.trim()))
        .collect(Collectors.toList());
  }

  public static Properties getKafkaProperties(
      KafkaConnectorProperties props, InboundConnectorContext context) {
    KafkaConnectorRequest connectorRequest = new KafkaConnectorRequest();
    connectorRequest.setTopic(props.getTopic());
    connectorRequest.setAuthentication(props.getAuthentication());
    connectorRequest.setAdditionalProperties(props.getAdditionalProperties());
    final Properties kafkaProps = connectorRequest.assembleKafkaClientProperties();
    if (kafkaProps.getProperty(ConsumerConfig.GROUP_ID_CONFIG) == null) {
      var groupIdConfig =
          DEFAULT_GROUP_ID_PREFIX
              + "-"
              + context.getDefinition().bpmnProcessId()
              + "-"
              + context.getDefinition().elementId()
              + "-"
              + context.getDefinition().processDefinitionKey();
      var limitedGroupIdConfig = groupIdConfig.substring(0, Math.min(groupIdConfig.length(), 250));
      kafkaProps.put(
          ConsumerConfig.GROUP_ID_CONFIG,
          limitedGroupIdConfig); // GROUP_ID_CONFIG is mandatory. It will be used to assign a
      // client id
    }
    kafkaProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, props.getAutoOffsetReset().toString());
    kafkaProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    kafkaProps.put(TopicConfig.RETENTION_MS_CONFIG, -1);
    kafkaProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, DEFAULT_KEY_DESERIALIZER);
    kafkaProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, DEFAULT_KEY_DESERIALIZER);
    return kafkaProps;
  }

  public static KafkaInboundMessage convertConsumerRecordToKafkaInboundMessage(
      ConsumerRecord<String, String> consumerRecord) {
    KafkaInboundMessage kafkaInboundMessage = new KafkaInboundMessage();
    kafkaInboundMessage.setKey(consumerRecord.key());
    kafkaInboundMessage.setRawValue(consumerRecord.value());
    try {
      var json = StringEscapeUtils.unescapeJson(consumerRecord.value());
      var jsonNode = objectMapper.readTree(json);
      kafkaInboundMessage.setValue(jsonNode);
    } catch (Exception e) {
      LOG.debug("Cannot parse value to json object -> use the raw value");
      kafkaInboundMessage.setValue(kafkaInboundMessage.getRawValue());
    }
    return kafkaInboundMessage;
  }
}
