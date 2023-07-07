/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.impl.LongStringHelper;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AMQPPropertyUtilTest {

  @Test
  void allPropertiesArePresent() {
    // given
    BasicProperties properties =
        new BasicProperties.Builder()
            .appId("appId")
            .clusterId("clusterId")
            .contentEncoding("contentEncoding")
            .contentType("contentType")
            .correlationId("correlationId")
            .deliveryMode(1)
            .expiration("expiration")
            .headers(Map.of("key", "value"))
            .messageId("messageId")
            .priority(1)
            .replyTo("replyTo")
            .timestamp(new Date())
            .type("type")
            .userId("userId")
            .build();

    // when
    var result = AMQPPropertyUtil.toProperties(properties);

    // then
    assertThat(result.appId()).isEqualTo(properties.getAppId());
    assertThat(result.clusterId()).isEqualTo(properties.getClusterId());
    assertThat(result.contentEncoding()).isEqualTo(properties.getContentEncoding());
    assertThat(result.contentType()).isEqualTo(properties.getContentType());
    assertThat(result.correlationId()).isEqualTo(properties.getCorrelationId());
    assertThat(result.deliveryMode()).isEqualTo(properties.getDeliveryMode());
    assertThat(result.expiration()).isEqualTo(properties.getExpiration());
    assertThat(result.headers()).isEqualTo(properties.getHeaders());
    assertThat(result.messageId()).isEqualTo(properties.getMessageId());
    assertThat(result.priority()).isEqualTo(properties.getPriority());
    assertThat(result.replyTo()).isEqualTo(properties.getReplyTo());
    assertThat(result.timestamp()).isEqualTo(properties.getTimestamp());
    assertThat(result.type()).isEqualTo(properties.getType());
    assertThat(result.userId()).isEqualTo(properties.getUserId());
  }

  @Test
  void longStringHeader_handledAsRegularString() {
    // given
    BasicProperties properties =
        new BasicProperties.Builder()
            .headers(Map.of("key", LongStringHelper.asLongString("value")))
            .build();

    // when
    var result = AMQPPropertyUtil.toProperties(properties);

    // then
    assertThat(result.headers().get("key")).isEqualTo("value");
  }

  @Test
  void listHeader_handledAsListOfPlainHeaders() {
    // given
    BasicProperties properties =
        new BasicProperties.Builder()
            .headers(
                Map.of(
                    "key",
                    List.of(
                        LongStringHelper.asLongString("value1"),
                        LongStringHelper.asLongString("value2"))))
            .build();

    // when
    var result = AMQPPropertyUtil.toProperties(properties);

    // then
    assertThat(result.headers().get("key")).isEqualTo(List.of("value1", "value2"));
  }

  @Test
  void longHeader_handledAsLong() {
    // given
    BasicProperties properties = new BasicProperties.Builder().headers(Map.of("key", 1L)).build();

    // when
    var result = AMQPPropertyUtil.toProperties(properties);

    // then
    assertThat(result.headers().get("key")).isEqualTo(1L);
  }
}
