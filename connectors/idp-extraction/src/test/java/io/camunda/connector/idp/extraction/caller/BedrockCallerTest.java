/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.ProviderConfiguration;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;

@ExtendWith(MockitoExtension.class)
class BedrockCallerTest {

  BedrockRuntimeClient bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
  ConverseResponse converseResponse = mock(ConverseResponse.class, Mockito.RETURNS_DEEP_STUBS);
  BedrockCaller bedrockCaller = new BedrockCaller();

  @Test
  void executeSuccessfulExtraction() {
    String expectedResponse =
        """
                {
                	"name": "John Smith",
                	"age": 32
                }
                """;

    when(bedrockRuntimeClient.converse(any(Consumer.class))).thenReturn(converseResponse);
    when(converseResponse.output().message().content().getFirst().text())
        .thenReturn(expectedResponse);

    AwsBaseRequest baseRequest = new AwsBaseRequest();
    ProviderConfiguration providerConfiguration = new ProviderConfiguration(baseRequest, null);
    ExtractionRequest extractionRequest =
        new ExtractionRequest(
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA, null, providerConfiguration);

    String bedrockResponse = bedrockCaller.call(extractionRequest, "", bedrockRuntimeClient);

    assertEquals(expectedResponse, bedrockResponse);
  }
}
