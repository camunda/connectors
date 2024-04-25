/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.client;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.response.JdbcResponse;

public sealed interface JdbcClient permits JdbiJdbcClient {
  JdbcResponse executeRequest(JdbcRequest request) throws ConnectorException;
}
