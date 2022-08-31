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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.amazonaws.services.lambda.model.InvokeResult;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AwsLambdaResultTest {

  @ParameterizedTest(name = "create result from ivokeResult")
  @CsvSource({
    "200,1.2,payload",
    "400,$LATEST,{\"1\":\"2\"}",
    "400,$LATEST,!@#$%^&*",
  })
  public void newAwsLambdaResult_shouldReturnResultWithCorrectData(
      Integer statusCode, String version, String payload) throws CharacterCodingException {
    // Given invoke result from aws lambda client
    ByteBuffer byteBufferPayload =
        StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(payload));
    InvokeResult invokeResult =
        new InvokeResult()
            .withStatusCode(statusCode)
            .withExecutedVersion(version)
            .withPayload(byteBufferPayload);
    // When
    AwsLambdaResult awsLambdaResult = new AwsLambdaResult(invokeResult);
    // Then
    assertThat(awsLambdaResult.getStatusCode()).isEqualTo(statusCode);
    assertThat(awsLambdaResult.getExecutedVersion()).isEqualTo(version);
    assertThat(awsLambdaResult.getPayload()).isEqualTo(payload);
  }
}
