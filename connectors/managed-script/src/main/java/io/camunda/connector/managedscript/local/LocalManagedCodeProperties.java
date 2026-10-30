/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.local;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the {@code local} managed-code provider.
 *
 * @param directory where the wrapper files are written, and the temporary directories of the calls
 *     are created; private to the user of the runtime (default {@code ~/.camunda/managed-script})
 */
@ConfigurationProperties(prefix = "camunda.connector.managed-code.local")
public record LocalManagedCodeProperties(Path directory) {

  public LocalManagedCodeProperties {
    if (directory == null) {
      directory = Path.of(System.getProperty("user.home"), ".camunda", "managed-script");
    }
  }
}
