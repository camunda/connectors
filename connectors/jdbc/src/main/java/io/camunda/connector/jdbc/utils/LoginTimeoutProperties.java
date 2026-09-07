/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;

import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import java.time.Duration;
import java.util.Properties;

final class LoginTimeoutProperties {

  private LoginTimeoutProperties() {}

  static void applyTo(Properties target, SupportedDatabase database, Duration timeout) {
    // These drivers read 0 as "no timeout", so anything under a second must not round down to it.
    String seconds = String.valueOf(Math.max(1L, timeout.toSeconds()));
    String millis = String.valueOf(Math.max(1L, timeout.toMillis()));
    switch (database) {
      case POSTGRESQL, MSSQL -> target.putIfAbsent("loginTimeout", seconds);
      case ORACLE -> target.putIfAbsent("oracle.jdbc.loginTimeout", seconds);
      // The MariaDB driver, used for MySQL too, has no login timeout: bound the socket connect and
      // the reads that carry the handshake instead.
      case MARIADB, MYSQL -> {
        target.putIfAbsent("connectTimeout", millis);
        target.putIfAbsent("socketTimeout", millis);
      }
    }
  }
}
