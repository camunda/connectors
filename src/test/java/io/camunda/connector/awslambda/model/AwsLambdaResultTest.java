/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.connector.awslambda.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.model.InvokeResult;
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
      Integer statusCode, String version, String payload) {
    // Given invoke result from aws lambda client
    ByteBuffer wrap = ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8));
    InvokeResult invokeResult =
        new InvokeResult()
            .withStatusCode(statusCode)
            .withExecutedVersion(version)
            .withPayload(wrap);

    // When
    AwsLambdaResult awsLambdaResult = new AwsLambdaResult(invokeResult, gson);
    // Then
    assertThat(awsLambdaResult.getStatusCode()).isEqualTo(statusCode);
    assertThat(awsLambdaResult.getExecutedVersion()).isEqualTo(version);
    assertThat(awsLambdaResult.getPayload()).isEqualTo(gson.fromJson(payload, Object.class));
  }
}
