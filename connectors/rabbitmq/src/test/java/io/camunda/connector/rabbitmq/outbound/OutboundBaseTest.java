/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

import io.camunda.connector.rabbitmq.BaseTest;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.IOException;
import java.util.stream.Stream;

public class OutboundBaseTest extends BaseTest {

  private static final String FAIL_REQUEST_VALIDATE_REQUIRED_FIELD_CASES_PATH =
      "src/test/resources/requests/outbound/fail-test-cases-validation-required-fields.json";

  private static final String FAIL_REQUEST_WITH_WRONG_PROPERTIES_FIELDS_CASES_PATH =
      "src/test/resources/requests/outbound/fail-test-cases-bad-message-properties.json";

  private static final String FAIL_PROPERTIES_FIELDS_VALIDATION_TEST_CASES_PATH =
      "src/test/resources/requests/outbound/fail-test-cases-properties-object-validation.json";

  private static final String SUCCESS_REQUEST_EXECUTE_CASES_PATH =
      "src/test/resources/requests/outbound/success-test-cases-execute-function.json";

  private static final String SUCCESS_REQUEST_WITH_PLAIN_TEXT_EXECUTE_CASES_PATH =
      "src/test/resources/requests/outbound/success-test-cases-with-plain-text-execute-function.json";

  private static final String SUCCESS_REPLACE_SECRETS_TEST_CASES_PATH =
      "src/test/resources/requests/outbound/success-test-cases-replace-secrets.json";

  public static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(SecretsConstant.Authentication.USERNAME, ActualValue.Authentication.USERNAME)
        .secret(SecretsConstant.Authentication.PASSWORD, ActualValue.Authentication.PASSWORD)
        .secret(SecretsConstant.Authentication.URI, ActualValue.Authentication.URI)
        .secret(SecretsConstant.Routing.HOST_NAME, ActualValue.Routing.HOST_NAME)
        .secret(SecretsConstant.Routing.VIRTUAL_HOST, ActualValue.Routing.VIRTUAL_HOST)
        .secret(SecretsConstant.Routing.ROUTING_KEY, ActualValue.Routing.ROUTING_KEY)
        .secret(SecretsConstant.Routing.EXCHANGE, ActualValue.Routing.EXCHANGE)
        .secret(SecretsConstant.Routing.PORT, ActualValue.Routing.PORT)
        .secret(SecretsConstant.Message.Body.VALUE, ActualValue.Message.Body.VALUE)
        .secret(
            SecretsConstant.Message.Properties.CONTENT_ENCODING,
            ActualValue.Message.Properties.CONTENT_ENCODING)
        .secret(
            SecretsConstant.Message.Properties.CONTENT_TYPE,
            ActualValue.Message.Properties.CONTENT_TYPE)
        .secret(
            SecretsConstant.Message.Properties.Headers.HEADER_KEY,
            ActualValue.Message.Properties.Headers.HEADER_KEY)
        .secret(
            SecretsConstant.Message.Properties.Headers.HEADER_VALUE,
            ActualValue.Message.Properties.Headers.HEADER_VALUE)
        .secret(
            SecretsConstant.Message.Properties.Headers.HEADER_VALUE,
            ActualValue.Message.Properties.Headers.HEADER_VALUE);
  }

  protected static Stream<String> failValidationRequiredFieldsTest() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_VALIDATE_REQUIRED_FIELD_CASES_PATH);
  }

  protected static Stream<String> failExecuteConnectorWithWrongPropertiesFields()
      throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_WITH_WRONG_PROPERTIES_FIELDS_CASES_PATH);
  }

  protected static Stream<String> failPropertiesFieldValidationTest() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_PROPERTIES_FIELDS_VALIDATION_TEST_CASES_PATH);
  }

  protected static Stream<String> successExecuteConnectorTest() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REQUEST_EXECUTE_CASES_PATH);
  }

  protected static Stream<String> successExecuteConnectorWithPlainTextTest() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REQUEST_WITH_PLAIN_TEXT_EXECUTE_CASES_PATH);
  }

  protected static Stream<String> successReplaceSecretsTest() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REPLACE_SECRETS_TEST_CASES_PATH);
  }
}
