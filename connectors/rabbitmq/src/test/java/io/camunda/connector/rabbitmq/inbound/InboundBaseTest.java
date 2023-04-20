/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import io.camunda.connector.rabbitmq.BaseTest;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import java.io.IOException;
import java.util.stream.Stream;

public class InboundBaseTest extends BaseTest {

  private static final String SUCCESS_REPLACE_SECRETS_TEST_CASES_PATH =
      "src/test/resources/requests/inbound/success-test-cases-replace-secrets.json";
  private static final String SUCCESS_RECEIVE_MESSAGE_TEST_CASES_PATH =
      "src/test/resources/requests/inbound/success-test-cases-consumer-valid-messages.json";

  public static InboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return InboundConnectorContextBuilder.create()
        .secret(SecretsConstant.Authentication.USERNAME, ActualValue.Authentication.USERNAME)
        .secret(SecretsConstant.Authentication.PASSWORD, ActualValue.Authentication.PASSWORD)
        .secret(SecretsConstant.Authentication.URI, ActualValue.Authentication.URI)
        .secret(SecretsConstant.Routing.HOST_NAME, ActualValue.Routing.HOST_NAME)
        .secret(SecretsConstant.Routing.VIRTUAL_HOST, ActualValue.Routing.VIRTUAL_HOST)
        .secret(SecretsConstant.Routing.PORT, ActualValue.Routing.PORT)
        .secret(SecretsConstant.QUEUE_NAME, ActualValue.QUEUE_NAME)
        .secret(SecretsConstant.CONSUMER_TAG, ActualValue.CONSUMER_TAG)
        .secret(SecretsConstant.QUEUE_TYPE, ActualValue.QUEUE_TYPE);
  }

  protected static Stream<String> successReplaceSecretsTest() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REPLACE_SECRETS_TEST_CASES_PATH);
  }
}
