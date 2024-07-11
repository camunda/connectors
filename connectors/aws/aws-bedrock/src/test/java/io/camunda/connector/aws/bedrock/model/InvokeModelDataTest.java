package io.camunda.connector.aws.bedrock.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.bedrock.BaseTest;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

class InvokeModelDataTest extends BaseTest {

  ObjectMapper mapper = new ObjectMapper();
  BedrockRuntimeClient bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
  InvokeModelResponse invokeModelResponse = mock(InvokeModelResponse.class);

  @Test
  void execute_success() throws JsonProcessingException {
    String payload =
        "{\"messages\":[{\"role\":\"user\", \"content\":\"Hello\"}], \"max_tokens\":256, \"top_p\":0.8, \"temperature\":0.7}";
    InvokeModelData invokeModelData = new InvokeModelData();
    invokeModelData.setModelId("random-model-id");
    invokeModelData.setPayload(mapper.readTree(payload));

    when(bedrockRuntimeClient.invokeModel(any(Consumer.class))).thenReturn(invokeModelResponse);
    when(invokeModelResponse.body()).thenReturn(SdkBytes.fromUtf8String("{\"result\" : \"Hey\"}"));
    BedrockResponse invokeModelWrappedResponse =
        invokeModelData.execute(bedrockRuntimeClient, mapper);

    Assertions.assertInstanceOf(InvokeModelWrappedResponse.class, invokeModelWrappedResponse);
  }

  @Test
  void execute_wrongReturn() throws JsonProcessingException {
    String payload =
        "{\"messages\":[{\"role\":\"user\", \"content\":\"Hello\"}], \"max_tokens\":256, \"top_p\":0.8, \"temperature\":0.7}";
    InvokeModelData invokeModelData = new InvokeModelData();
    invokeModelData.setModelId("random-model-id");
    invokeModelData.setPayload(mapper.readTree(payload));

    when(bedrockRuntimeClient.invokeModel(any(Consumer.class))).thenReturn(invokeModelResponse);
    when(invokeModelResponse.body()).thenReturn(SdkBytes.fromUtf8String("Hey"));

    RuntimeException runtimeException =
        Assertions.assertThrows(
            RuntimeException.class, () -> invokeModelData.execute(bedrockRuntimeClient, mapper));

    assertEquals("Unrecognized token 'Hey': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')", runtimeException.getMessage());
  }
}
