/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.spi;

/** Thrown by the sandbox SPI layer on operational failures (e.g., file not found, exec error). */
public class SandboxException extends RuntimeException {

  public SandboxException(String message) {
    super(message);
  }

  public SandboxException(String message, Throwable cause) {
    super(message, cause);
  }
}
