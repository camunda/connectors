/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.model.InvokeResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.awslambda.BaseTest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AwsLambdaResultTest extends BaseTest {

  @ParameterizedTest(name = "create result from invokeResult #{index}")
  @CsvSource({
    "200,1.2,{\"body\": {\"key\":\"value\"}}",
    "400,$LATEST,{\"1\":\"2\"}",
    "400,$LATEST,{\"!@#$%^&*\":\"!@#$%^&*\"}"
  })
  public void newAwsLambdaResult_shouldReturnResultWithCorrectData(
      Integer statusCode, String version, String payload) throws JsonProcessingException {
    // Given invoke result from aws lambda client
    ByteBuffer wrap = ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8));
    InvokeResult invokeResult =
        new InvokeResult()
            .withStatusCode(statusCode)
            .withExecutedVersion(version)
            .withPayload(wrap);

    // When
    AwsLambdaResult awsLambdaResult = new AwsLambdaResult(invokeResult, objectMapper);
    // Then
    assertThat(awsLambdaResult.getStatusCode()).isEqualTo(statusCode);
    assertThat(awsLambdaResult.getExecutedVersion()).isEqualTo(version);
    assertThat(awsLambdaResult.getPayload())
        .isEqualTo(objectMapper.readValue(payload, Object.class));
  }
}
