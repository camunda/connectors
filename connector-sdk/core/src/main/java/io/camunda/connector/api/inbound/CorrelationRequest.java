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

/**
 * Represents a request to correlate an inbound event with a process definition. This request
 * includes the variables necessary for correlation and a message ID.
 *
 * <p>The specified message ID will only be used as a fallback value if a custom message ID
 * expression is not configured in the connector's element template.
 *
 * @see <a
 *     href="https://docs.camunda.io/docs/components/concepts/messages/#message-correlation-overview">Message
 *     correlation</a>
 *     <p>Instances of this class are immutable and should be created using the {@link Builder}
 *     inner class.
 * @see CorrelationResult
 * @see CorrelationFailureHandlingStrategy
 */
public class CorrelationRequest {
  private final Object variables;
  private final String messageId;

  /**
   * Constructs a new {@code CorrelationRequest} with the specified variables and message ID.
   *
   * @param variables the inbound connector variables required for correlation
   * @param messageId the unique identifier of the message. It will only be used as a fallback value
   *     if a custom message ID expression is not configured in the connector's element template.
   */
  public CorrelationRequest(Object variables, String messageId) {
    this.variables = variables;
    this.messageId = messageId;
  }

  /**
   * Returns a new {@link Builder} instance for creating a {@code CorrelationRequest}.
   *
   * @return a {@link Builder} instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the message ID of this correlation request.
   *
   * @return the message ID
   */
  public String getMessageId() {
    return messageId;
  }

  /**
   * Returns the variables of this correlation request.
   *
   * @return the variables
   */
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

  /** A builder for creating instances of {@link CorrelationRequest}. */
  public static class Builder {
    private Object variables;
    private String messageId;

    /**
     * Sets the variables for the {@code CorrelationRequest} being built.
     *
     * @param variables the inbound connector variables
     * @return this {@code Builder} instance
     */
    public Builder variables(Object variables) {
      this.variables = variables;
      return this;
    }

    /**
     * Sets the message ID for the {@code CorrelationRequest} being built.
     *
     * @param messageId the unique identifier of the message
     * @return this {@code Builder} instance
     */
    public Builder messageId(String messageId) {
      this.messageId = messageId;
      return this;
    }

    /**
     * Builds and returns a {@code CorrelationRequest} instance with the specified values.
     *
     * @return a new {@code CorrelationRequest} instance
     */
    public CorrelationRequest build() {
      return new CorrelationRequest(variables, messageId);
    }
  }
}
