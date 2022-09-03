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

package io.camunda.connector.awslambda;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.camunda.connector.test.ConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public abstract class BaseTest {

  protected static final Gson gson = new GsonBuilder().create();

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
      gson.fromJson(ACTUAL_STRING_PAYLOAD, Object.class); // toObject(ACTUAL_STRING_PAYLOAD);
  protected static final ByteBuffer ACTUAL_BYTEBUFFER_PAYLOAD =
      ByteBuffer.wrap(ACTUAL_STRING_PAYLOAD.getBytes(StandardCharsets.UTF_8));

  protected static final String EXECUTED_VERSION = "LATEST";

  protected static final String SUCCESS_REQUEST_CASE_PATH =
      "src/test/resources/requests/lambda-connector-success-test-case.json";

  protected static final String SUCCESS_REQUEST_WITH_SECRETS_CASE_PATH =
      "src/test/resources/requests/lambda-connector-success-request-with-secrets-test-case.json";

  protected static final String FAIL_REQUEST_CASE_PATH =
      "src/test/resources/requests/lambda-connector-fail-test-case.json";

  protected static Stream<String> successRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_REQUEST_CASE_PATH);
  }

  protected static Stream<String> successSecretsRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(SUCCESS_REQUEST_WITH_SECRETS_CASE_PATH);
  }

  protected static Stream<String> failRequestCases() throws IOException {
    return BaseTest.loadTestCasesFromResourceFile(FAIL_REQUEST_CASE_PATH);
  }

  protected static ConnectorContextBuilder getContextBuilderWithSecrets() {
    return ConnectorContextBuilder.create()
        .secret(SECRET_KEY, ACTUAL_SECRET_KEY)
        .secret(ACCESS_KEY, ACTUAL_ACCESS_KEY)
        .secret(FUNCTION_REGION_KEY, ACTUAL_FUNCTION_REGION)
        .secret(FUNCTION_NAME_KEY, ACTUAL_FUNCTION_NAME);
  }

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    final Gson testingGson = new Gson();
    var array = testingGson.fromJson(cases, ArrayList.class);
    return array.stream().map(testingGson::toJson).map(Arguments::of);
  }
}
