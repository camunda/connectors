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

import java.util.Map;

public class ConnectorExceptionBuilder {

  protected String errorCode;
  protected String message;
  protected Throwable cause;
  protected Map<String, Object> errorVariables;

  public ConnectorExceptionBuilder errorCode(String errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  public ConnectorExceptionBuilder message(String message) {
    this.message = message;
    return this;
  }

  public ConnectorExceptionBuilder cause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  public ConnectorExceptionBuilder errorVariables(Map<String, Object> errorVariables) {
    this.errorVariables = errorVariables;
    return this;
  }

  public ConnectorException build() {
    return new ConnectorException(errorCode, message, cause, errorVariables);
  }
}
