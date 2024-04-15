/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.outbound;

import io.camunda.connector.jdbc.BaseTest;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;

public class OutboundBaseTest extends BaseTest {

  public static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(SecretsConstant.Authentication.USERNAME, ActualValue.Authentication.USERNAME)
        .secret(SecretsConstant.Authentication.PASSWORD, ActualValue.Authentication.PASSWORD)
        .secret(SecretsConstant.Authentication.URI, ActualValue.Authentication.URI)
        .secret(SecretsConstant.Authentication.PORT, ActualValue.Authentication.PORT)
        .secret(SecretsConstant.Authentication.HOST, ActualValue.Authentication.HOST)
        .secret(SecretsConstant.Query.QUERY, ActualValue.Query.QUERY)
        .secret(SecretsConstant.Variables.VARIABLES, ActualValue.Variables.VARIABLES);
  }
}
