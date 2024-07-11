package io.camunda.connector.aws.bedrock.model;

import static org.junit.jupiter.api.Assertions.*;
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
    previousMessages.add(new PreviousMessage("Hey", ConversationRole.USER));
    previousMessages.add(new PreviousMessage("How are you?", ConversationRole.ASSISTANT));
    converseData.setMessagesHistory(previousMessages);
    converseData.setNewMessage("I am good thanks, and you?");

    when(bedrockRuntimeClient.converse(any(Consumer.class))).thenReturn(converseResponse);
    when(converseResponse.output().message().content().getFirst().text())
        .thenReturn("I am also good");
    BedrockResponse bedrockResponse = converseData.execute(bedrockRuntimeClient, mapper);

    Assertions.assertInstanceOf(ConverseWrapperResponse.class, bedrockResponse);
    Assertions.assertEquals(
        "I am also good",
        ((ConverseWrapperResponse) bedrockResponse).messagesHistory().getLast().getMessage());
  }
}
