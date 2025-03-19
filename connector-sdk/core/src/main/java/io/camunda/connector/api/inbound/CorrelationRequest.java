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
package io.camunda.connector.api.inbound;

import java.util.Objects;

public class CorrelationRequest {
  private final Object variables;
  private final String messageId;

  public CorrelationRequest(Object variables, String messageId) {
    this.variables = variables;
    this.messageId = messageId;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getMessageId() {
    return messageId;
  }

  public Object getVariables() {
    return variables;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    CorrelationRequest that = (CorrelationRequest) o;
    return Objects.equals(variables, that.variables) && Objects.equals(messageId, that.messageId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(variables, messageId);
  }

  public static class Builder {
    private Object variables;
    private String messageId;

    public Builder variables(Object variables) {
      this.variables = variables;
      return this;
    }

    public Builder messageId(String messageId) {
      this.messageId = messageId;
      return this;
    }

    public CorrelationRequest build() {
      return new CorrelationRequest(variables, messageId);
    }
  }
}
