/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.api.error;

import java.io.Serial;
import java.util.Map;

/**
 * Unchecked exception indicating issues with a connector.
 *
 * @see ConnectorExceptionBuilder
 */
public class ConnectorException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  protected String errorCode;

  protected Map<String, Object> errorVariables;

  public ConnectorException(Throwable cause) {
    super(cause);
  }

  public ConnectorException(String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the specified error code and message.
   *
   * @param errorCode the error code to populate
   * @param message the message detailing what went wrong
   */
  public ConnectorException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  /**
   * Constructs a new exception with the specified error code and the specified cause and a detail
   * message of {@code (cause==null ? null : cause.toString())} (which typically contains the class
   * and detail message of {@code cause}).
   *
   * @param errorCode the error code to populate
   * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
   *     (A <code>null</code> value is permitted, and indicates that the cause is nonexistent or
   *     unknown.)
   */
  public ConnectorException(String errorCode, Throwable cause) {
    super(cause);
    this.errorCode = errorCode;
  }

  /**
   * Constructs a new exception with the specified error code, message, and the specified cause.
   *
   * @param errorCode the error code to populate
   * @param message the message detailing what went wrong
   * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
   *     (A <code>null</code> value is permitted, and indicates that the cause is nonexistent or
   *     unknown.)
   */
  public ConnectorException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  /**
   * Constructs a new exception with the specified error code, message, cause, and error variables.
   *
   * @param errorCode the error code to populate
   * @param message the message detailing what went wrong
   * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
   *     (A <code>null</code> value is permitted, and indicates that the cause is nonexistent or
   *     unknown.)
   * @param errorVariables the error variables to populate
   * @see ConnectorExceptionBuilder
   */
  ConnectorException(
      String errorCode, String message, Throwable cause, Map<String, Object> errorVariables) {
    this(errorCode, message, cause);
    this.errorVariables = errorVariables;
  }

  /**
   * @return the error code
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * @return the error variables
   */
  public Map<String, Object> getErrorVariables() {
    return errorVariables;
  }

  @Override
  public String toString() {
    return "ConnectorException{"
        + "errorCode='"
        + errorCode
        + '\''
        + ", errorVariables="
        + errorVariables
        + '}'
        + (getCause() != null ? ", cause=" + getCause().getMessage() : "");
  }
}
