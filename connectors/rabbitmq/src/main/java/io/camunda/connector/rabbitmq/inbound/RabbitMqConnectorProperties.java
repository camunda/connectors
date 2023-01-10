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
package io.camunda.connector.rabbitmq.inbound;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.api.inbound.InboundConnectorProperties;

public class RabbitMqConnectorProperties {

  private final InboundConnectorProperties genericProperties;

  private String activationCondition;
  private String variableMapping;
  private String uri;
  private String queueName;

  public RabbitMqConnectorProperties(InboundConnectorProperties properties) {
    this.genericProperties = properties;

    this.activationCondition = readPropertyNullable("inbound.activationCondition");
    this.variableMapping = readPropertyNullable("inbound.variableMapping");
    this.uri = readPropertyRequired("inbound.uri");
    this.queueName = readPropertyRequired("inbound.queueName");
  }

  public String getConnectorIdentifier() {
    return ""
      + genericProperties.getType()
      + "-"
      + genericProperties.getBpmnProcessId()
      + "-"
      + genericProperties.getVersion();
  }

  protected String readPropertyRequired(String propertyName) {
    String result = readPropertyNullable(propertyName);
    if (result == null) {
      throw new IllegalArgumentException(
              "Property '" + propertyName + "' must be set for connector");
    }
    return result;
  }
  protected String readPropertyNullable(String propertyName) {
    return genericProperties.getProperties().get(propertyName);
  }

  public String getActivationCondition() {
    return activationCondition;
  }

  public void setActivationCondition(String activationCondition) {
    this.activationCondition = activationCondition;
  }

  public String getVariableMapping() {
    return variableMapping;
  }

  public void setVariableMapping(String variableMapping) {
    this.variableMapping = variableMapping;
  }

  public String getUri() {
    return uri;
  }
  public String getQueueName() {
    return queueName;
  }

  public String getBpmnProcessId() {
    return genericProperties.getBpmnProcessId();
  }

  public int getVersion() {
    return genericProperties.getVersion();
  }

  public String getType() {
    return genericProperties.getType();
  }

  public long getProcessDefinitionKey() {
    return genericProperties.getProcessDefinitionKey();
  }

  @Override
  public String toString() {
    return "RabbitMqConnectorProperties-" + genericProperties.toString();
  }
}
