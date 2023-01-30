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
package io.camunda.connector.impl.inbound;

import io.camunda.connector.api.inbound.ProcessCorrelationPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Properties of a message published by an Inbound Connector */
public class MessageCorrelationPoint extends ProcessCorrelationPoint {

  public static final String TYPE_NAME = "MESSAGE";

  private static final Logger LOG = LoggerFactory.getLogger(MessageCorrelationPoint.class);
  private final String messageName;

  /** FEEL expression */
  private final String correlationKeyExpression;

  public MessageCorrelationPoint(String messageName, String correlationKeyExpression) {
    this.messageName = messageName;
    this.correlationKeyExpression = correlationKeyExpression;
    LOG.debug("Created inbound correlation point: " + this);
  }

  public String getMessageName() {
    return messageName;
  }

  public String getCorrelationKeyExpression() {
    return correlationKeyExpression;
  }

  @Override
  public String toString() {
    return "MessageCorrelationPoint{"
        + "messageName='"
        + messageName
        + '\''
        + ", correlationKeyMapping='"
        + correlationKeyExpression
        + '}';
  }

  @Override
  public int compareTo(ProcessCorrelationPoint o) {
    if (!this.getClass().equals(o.getClass())) {
      return 1;
    }
    MessageCorrelationPoint other = (MessageCorrelationPoint) o;
    if (!messageName.equals(other.messageName)) {
      return messageName.compareTo(other.messageName);
    }
    return correlationKeyExpression.compareTo(other.correlationKeyExpression);
  }
}
