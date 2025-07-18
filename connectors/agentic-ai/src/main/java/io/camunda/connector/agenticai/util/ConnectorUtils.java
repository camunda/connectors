/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

public final class ConnectorUtils {
  private ConnectorUtils() {}

  public static boolean isSaaS() {
    return System.getenv().containsKey("CAMUNDA_CONNECTOR_RUNTIME_SAAS");
  }
}
