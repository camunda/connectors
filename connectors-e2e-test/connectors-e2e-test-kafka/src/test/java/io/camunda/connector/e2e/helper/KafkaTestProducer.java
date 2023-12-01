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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringSerializer;

public class KafkaTestProducer {
  private final KafkaProducer<Object, String> producer;
  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  public KafkaTestProducer(String bootstrapServers) {
    Properties props = new Properties();
    props.put("bootstrap.servers", bootstrapServers);
    props.put("key.serializer", StringSerializer.class.getName());
    props.put("value.serializer", StringSerializer.class.getName());
    this.producer = new KafkaProducer<>(props);
  }

  public void sendMessage(String topic, Object key, String value, Map<String, String> headersMap)
      throws JsonProcessingException {
    String keyAsString =
        key instanceof String ? (String) key : objectMapper.writeValueAsString(key);

    Headers headers = new RecordHeaders();
    headersMap.forEach(
        (k, v) -> headers.add(new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8))));

    ProducerRecord<Object, String> record =
        new ProducerRecord<>(topic, null, keyAsString, value, headers);
    producer.send(
        record,
        (metadata, exception) -> {
          if (exception != null) {
            this.close();
            throw new RuntimeException(exception);
          }
        });
  }

  public AtomicBoolean startContinuousMessageSending(
      String topic, Object messageKey, String messageValue, Map<String, String> headers) {
    AtomicBoolean atomicBoolean = new AtomicBoolean(true);
    Thread thread =
        new Thread(
            () -> {
              while (atomicBoolean.get()) {
                try {
                  sendMessage(topic, messageKey, messageValue, headers);
                  Thread.sleep(1000);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }
            });
    thread.setDaemon(true);
    thread.start();
    return atomicBoolean;
  }

  public void close() {
    if (producer != null) {
      producer.close();
    }
  }
}
