/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc;

import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;

public class OutboundBaseTest extends BaseTest {

  public static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(SecretsConstant.Connection.USERNAME, ActualValue.Connection.USERNAME)
        .secret(SecretsConstant.Connection.PASSWORD, ActualValue.Connection.PASSWORD)
        .secret(SecretsConstant.Connection.URI, ActualValue.Connection.URI)
        .secret(SecretsConstant.Connection.PORT, ActualValue.Connection.PORT)
        .secret(SecretsConstant.Connection.HOST, ActualValue.Connection.HOST)
        .secret(SecretsConstant.Data.Query.QUERY, ActualValue.Data.Query.QUERY)
        .secret(SecretsConstant.Data.Variables.VARIABLES, ActualValue.Data.Variables.VARIABLES);
  }
}
