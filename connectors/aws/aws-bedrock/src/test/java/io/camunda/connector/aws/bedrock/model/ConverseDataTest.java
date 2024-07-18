/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;

class ConverseDataTest {

  ObjectMapper mapper = new ObjectMapper();
  BedrockRuntimeClient bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
  ConverseResponse converseResponse = mock(ConverseResponse.class, Mockito.RETURNS_DEEP_STUBS);

  @Test
  void execute_success() {

    ConverseData converseData = new ConverseData();
    converseData.setModelId("random-model-id");

    List<PreviousMessage> previousMessages = new ArrayList<>();
    previousMessages.add(new PreviousMessage("Hey", ConversationRole.USER.name()));
    previousMessages.add(new PreviousMessage("How are you?", ConversationRole.ASSISTANT.name()));
    converseData.setMessages(previousMessages);
    converseData.setNextMessage("I am good thanks, and you?");

    when(bedrockRuntimeClient.converse(any(Consumer.class))).thenReturn(converseResponse);
    when(converseResponse.output().message().content().getFirst().text())
        .thenReturn("I am also good");
    BedrockResponse bedrockResponse = converseData.execute(bedrockRuntimeClient, mapper);

    Assertions.assertInstanceOf(ConverseWrapperResponse.class, bedrockResponse);
    Assertions.assertEquals(
        "I am also good",
        ((ConverseWrapperResponse) bedrockResponse).messagesHistory().getLast().message());
  }
}
