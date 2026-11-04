/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.a2a.client.common.model.part.A2aTextPart;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aPartConverterImpl;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aSdkObjectConverterImpl;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class A2aTaskNotificationParserTest {

  private final A2aTaskNotificationParser parser =
      new A2aTaskNotificationParserImpl(new A2aSdkObjectConverterImpl(new A2aPartConverterImpl()));

  @Test
  void parsesWrappedTaskNotification() throws Exception {
    Task task =
        Task.builder()
            .id("task-1")
            .contextId("ctx-1")
            .status(
                new TaskStatus(
                    TaskState.TASK_STATE_COMPLETED,
                    Message.builder()
                        .messageId("msg-1")
                        .contextId("ctx-1")
                        .role(Message.Role.ROLE_AGENT)
                        .parts(List.of(new TextPart("Done", null)))
                        .build(),
                    null))
            .build();
    String json = JsonUtil.toJsonStreamingEvent(task);

    var result = parser.parse(json.getBytes(StandardCharsets.UTF_8));

    assertThat(json).contains("\"task\"");
    assertThat(result.id()).isEqualTo("task-1");
    assertThat(result.contextId()).isEqualTo("ctx-1");
    assertThat(result.status().state()).isEqualTo(A2aTaskStatus.TaskState.COMPLETED);
    assertThat(result.status().message().contents())
        .singleElement()
        .isInstanceOfSatisfying(
            A2aTextPart.class, part -> assertThat(part.text()).isEqualTo("Done"));
  }

  @Test
  void rejectsInvalidJson() {
    assertThatThrownBy(() -> parser.parse("invalid json".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonTaskNotification() throws Exception {
    Message message =
        Message.builder()
            .messageId("msg-1")
            .role(Message.Role.ROLE_AGENT)
            .parts(List.of(new TextPart("Hi", null)))
            .build();
    String json = JsonUtil.toJsonStreamingEvent(message);

    assertThatThrownBy(() -> parser.parse(json.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Only task notifications");
  }
}
