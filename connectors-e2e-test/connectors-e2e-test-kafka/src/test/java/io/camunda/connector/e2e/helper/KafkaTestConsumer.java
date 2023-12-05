/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.helper;

import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.kafka.inbound.KafkaInboundMessage;
import io.camunda.connector.kafka.inbound.KafkaPropertyTransformer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

public class KafkaTestConsumer {

  private final KafkaConsumer<Object, Object> consumer;

  public KafkaTestConsumer(String bootstrapServers, String groupId, String topic) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    this.consumer = new KafkaConsumer<>(props);
    this.consumer.subscribe(Collections.singletonList(topic));
  }

  public List<KafkaInboundMessage> pollMessages(int count, long timeoutMillis) {
    List<KafkaInboundMessage> result = new ArrayList<>();
    int messageCount = 0;
    long startTime = System.currentTimeMillis();

    try {
      while (messageCount < count && (System.currentTimeMillis() - startTime) < timeoutMillis) {
        ConsumerRecords<Object, Object> records = consumer.poll(Duration.ofMillis(100));
        for (var record : records) {

          KafkaInboundMessage kafkaMessage =
              KafkaPropertyTransformer.convertConsumerRecordToKafkaInboundMessage(
                  record, ConnectorsObjectMapperSupplier.getCopy().reader());
          result.add(kafkaMessage);
          messageCount++;
          if (messageCount >= count) {
            break;
          }
        }
      }
    } finally {
      consumer.close();
    }
    return result;
  }

  public void close() {
    consumer.wakeup();
  }
}
