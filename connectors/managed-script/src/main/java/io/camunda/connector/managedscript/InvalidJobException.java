/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import io.camunda.connector.managedscript.api.ErrorCode;

/** The job cannot succeed on any retry, for example because its headers are invalid. */
final class InvalidJobException extends RuntimeException {

  private final ErrorCode code;

  InvalidJobException(String message) {
    this(ErrorCode.INVALID_JOB, message);
  }

  InvalidJobException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  ErrorCode code() {
    return code;
  }
}
