/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.awslambda.BaseTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

class AwsLambdaResultTest extends BaseTest {

  @ParameterizedTest(name = "create result from invokeResponse #{index}")
  @CsvSource({
    "200,1.2,{\"body\": {\"key\":\"value\"}}",
    "400,$LATEST,{\"1\":\"2\"}",
    "400,$LATEST,{\"!@#$%^&*\":\"!@#$%^&*\"}"
  })
  public void newAwsLambdaResult_shouldReturnResultWithCorrectData(
      Integer statusCode, String version, String payload) throws JsonProcessingException {
    // Given invoke response from aws lambda client
    InvokeResponse invokeResponse =
        InvokeResponse.builder()
            .statusCode(statusCode)
            .executedVersion(version)
            .payload(SdkBytes.fromUtf8String(payload))
            .build();

    // When
    AwsLambdaResult awsLambdaResult = new AwsLambdaResult(invokeResponse, objectMapper);
    // Then
    assertThat(awsLambdaResult.getStatusCode()).isEqualTo(statusCode);
    assertThat(awsLambdaResult.getExecutedVersion()).isEqualTo(version);
    assertThat(awsLambdaResult.getPayload())
        .isEqualTo(objectMapper.readValue(payload, Object.class));
  }
}
