/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.envelope.ModelCallEnvelope;
import java.util.Objects;
import org.springframework.test.util.ReflectionTestUtils;

/** Gives factory tests access to the provider chat model a factory wraps in its envelope. */
public final class ModelCallEnvelopeFixture {

  private ModelCallEnvelopeFixture() {}

  public static ChatModel delegateOf(ChatModel chatModel) {
    assertThat(chatModel).isInstanceOf(ModelCallEnvelope.class);
    return Objects.requireNonNull((ChatModel) ReflectionTestUtils.getField(chatModel, "delegate"));
  }
}
