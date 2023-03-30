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
package io.camunda.connector.impl.inbound.result;

import java.util.Objects;

public class CorrelationErrorData {

  private final CorrelationErrorReason reason;
  private final String message;

  public enum CorrelationErrorReason {
    ACTIVATION_CONDITION_NOT_MET
  }

  public CorrelationErrorData(CorrelationErrorReason reason, String message) {
    this.reason = reason;
    this.message = message;
  }

  public CorrelationErrorData(CorrelationErrorReason reason) {
    this.reason = reason;
    this.message = null;
  }

  public CorrelationErrorReason getReason() {
    return reason;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CorrelationErrorData that = (CorrelationErrorData) o;
    return reason == that.reason && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(reason, message);
  }

  @Override
  public String toString() {
    return "CorrelationErrorData{" + "reason=" + reason + ", message='" + message + '\'' + '}';
  }
}
