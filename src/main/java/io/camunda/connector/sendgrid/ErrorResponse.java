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
package io.camunda.connector.sendgrid;

import java.util.Arrays;
import java.util.Objects;

public class ErrorResponse {
  private String message;
  private StackTraceElement[] stackTrace;

  public ErrorResponse(final Throwable throwable) {
    message = throwable.getMessage();
    stackTrace = throwable.getStackTrace();
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public StackTraceElement[] getStackTrace() {
    return stackTrace;
  }

  public void setStackTrace(final StackTraceElement[] stackTrace) {
    this.stackTrace = stackTrace;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ErrorResponse that = (ErrorResponse) o;
    return Objects.equals(message, that.message) && Arrays.equals(stackTrace, that.stackTrace);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(message);
    result = 31 * result + Arrays.hashCode(stackTrace);
    return result;
  }

  @Override
  public String toString() {
    return "ErrorResponse{"
        + "message='"
        + message
        + '\''
        + ", stackTrace="
        + Arrays.toString(stackTrace)
        + '}';
  }
}
