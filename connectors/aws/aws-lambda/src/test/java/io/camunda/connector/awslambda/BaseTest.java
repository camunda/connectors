/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.awslambda.model.AwsLambdaRequest;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

public abstract class BaseTest {

  protected static final ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();

  protected static final String SECRET_KEY = "SECRET_KEY";
  protected static final String ACTUAL_SECRET_KEY = "testSecretKey";
  protected static final String ACCESS_KEY = "ACCESS_KEY";
  protected static final String ACTUAL_ACCESS_KEY = "AKIAUTEST1234OOUNTYOWU";
  protected static final String FUNCTION_REGION_KEY = "REGION_KEY";
  protected static final String ACTUAL_FUNCTION_REGION = "us-east-1";
  protected static final String ACTUAL_FUNCTION_NAME =
      "arn:aws:lambda:us-east-1:1234567891017:function:cam";
  protected static final String FUNCTION_NAME_KEY = "FUNCTION_NAME";

  protected static final String ACTUAL_STRING_PAYLOAD = "{\"event\":{\"key\":\"value\"}}";
  protected static final Object ACTUAL_PAYLOAD =
      objectMapper.convertValue(
          ACTUAL_STRING_PAYLOAD, Object.class); // toObject(ACTUAL_STRING_PAYLOAD);
  protected static final ByteBuffer ACTUAL_BYTEBUFFER_PAYLOAD =
      ByteBuffer.wrap(ACTUAL_STRING_PAYLOAD.getBytes(StandardCharsets.UTF_8));

  protected static final String EXECUTED_VERSION = "LATEST";

  protected static final String SUCCESS_REQUEST_CASE_PATH =
      "src/test/resources/requests/lambda-connector-success-test-case.json";

  protected static final String SUCCESS_REQUEST_WITH_SECRETS_CASE_PATH =
      "src/test/resources/requests/lambda-connector-success-request-with-secrets-test-case.json";

  protected static final String FAIL_REQUEST_CASE_PATH =
      "src/test/resources/requests/lambda-connector-fail-test-case.json";

  protected static Stream<AwsLambdaRequest> successRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_REQUEST_CASE_PATH);
  }

  protected static Stream<AwsLambdaRequest> successSecretsRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_REQUEST_WITH_SECRETS_CASE_PATH);
  }

  protected static Stream<AwsLambdaRequest> failRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(FAIL_REQUEST_CASE_PATH);
  }

  protected static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(SECRET_KEY, ACTUAL_SECRET_KEY)
        .secret(ACCESS_KEY, ACTUAL_ACCESS_KEY)
        .secret(FUNCTION_REGION_KEY, ACTUAL_FUNCTION_REGION)
        .secret(FUNCTION_NAME_KEY, ACTUAL_FUNCTION_NAME);
  }

  protected static Stream<AwsLambdaRequest> loadTestCasesFromResourceFile(
      final String fileWithTestCasesUri) throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    return objectMapper.readValue(cases, new TypeReference<List<AwsLambdaRequest>>() {}).stream();
  }
}
