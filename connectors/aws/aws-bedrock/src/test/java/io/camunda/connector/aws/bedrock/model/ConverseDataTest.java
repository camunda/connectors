/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

class ConverseDataTest {

  ObjectMapper mapper = new ObjectMapper();
  BedrockRuntimeClient bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
  ConverseResponse converseResponse = mock(ConverseResponse.class, Mockito.RETURNS_DEEP_STUBS);

  @Test
  void execute_success() {

    ConverseData converseData = new ConverseData();
    converseData.setModelId("random-model-id");

    List<BedrockMessage> previousMessages = new ArrayList<>();
    previousMessages.add(
        new BedrockMessage("assistant", List.of(new BedrockContent("Hey, How are you?"))));

    converseData.setMessagesHistory(previousMessages);
    converseData.setNewMessage("I am good thanks, and you?");

    when(bedrockRuntimeClient.converse(any(Consumer.class))).thenReturn(converseResponse);
    Message response =
        Message.builder()
            .role("assistant")
            .content(ContentBlock.fromText("I am also good"))
            .build();

    when(converseResponse.output().message()).thenReturn(response);

    List<BedrockMessage> result = converseData.execute(bedrockRuntimeClient, mapper);

    assertThat(result.size()).isEqualTo(3);
    assertThat(result.get(2).getContentList())
        .isEqualTo(List.of(new BedrockContent("I am also good")));
  }
}
