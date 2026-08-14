/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageIdValueTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void serializesAsBareJsonString() throws Exception {
    var id = MessageId.of(UUID.fromString("018f5b3a-1234-7abc-8def-0123456789ab"));

    var serialized = objectMapper.writeValueAsString(id);

    assertThat(serialized).isEqualTo("\"018f5b3a-1234-7abc-8def-0123456789ab\"");
  }

  @Test
  void deserializesFromBareJsonString() throws Exception {
    var id = objectMapper.readValue("\"018f5b3a-1234-7abc-8def-0123456789ab\"", MessageId.class);

    assertThat(id).isEqualTo(MessageId.of(UUID.fromString("018f5b3a-1234-7abc-8def-0123456789ab")));
  }

  @Test
  void acceptsAnyUuidVersionOnParse() {
    var v4 = UUID.randomUUID();

    assertThat(MessageId.of(v4.toString())).isEqualTo(MessageId.of(v4));
  }

  @Test
  void rejectsMalformedUuidSyntax() {
    assertThatThrownBy(() -> MessageId.of("not-a-uuid"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void equalsAndHashCodeAreValueBased() {
    var uuid = UUID.randomUUID();

    assertThat(MessageId.of(uuid)).isEqualTo(MessageId.of(uuid));
    assertThat(MessageId.of(uuid)).hasSameHashCodeAs(MessageId.of(uuid));
  }
}
