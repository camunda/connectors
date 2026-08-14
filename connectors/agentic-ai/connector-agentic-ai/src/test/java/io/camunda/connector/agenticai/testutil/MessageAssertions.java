/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.testutil;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import java.util.List;

public class MessageAssertions {

  private MessageAssertions() {}

  /**
   * Asserts {@code actual} equals {@code expected}, in order. Ignores {@link Message#id()} on any
   * {@link SystemMessage}: the SUT always builds its own fresh instance, so the id can't match.
   */
  public static void assertMessagesEqualIgnoringSystemMessageId(
      List<? extends Message> actual, Message... expected) {
    assertThat(actual).hasSize(expected.length);
    for (int i = 0; i < expected.length; i++) {
      if (expected[i] instanceof SystemMessage) {
        assertThat(actual.get(i))
            .usingRecursiveComparison()
            .ignoringFields("id")
            .isEqualTo(expected[i]);
      } else {
        assertThat(actual.get(i)).isEqualTo(expected[i]);
      }
    }
  }
}
