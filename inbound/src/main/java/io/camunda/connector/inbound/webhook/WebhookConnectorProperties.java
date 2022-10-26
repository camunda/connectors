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
package io.camunda.connector.inbound.webhook;

import io.camunda.connector.inbound.registry.InboundConnectorProperties;
import io.camunda.connector.inbound.security.signature.HMACSwitchCustomerChoice;

public class WebhookConnectorProperties {

    public InboundConnectorProperties genericProperties;

    public WebhookConnectorProperties(InboundConnectorProperties properties) {
        this.genericProperties = properties;
    }

    public String getConnectorIdentifier() {
        return "" + genericProperties.getType() + "-" + getContext() + "-" + genericProperties.getBpmnProcessId() + "-" + genericProperties.getVersion();
    }
    public String readProperty(String propertyName) {
        String result = genericProperties.getProperties().get(propertyName);
        if (result==null) {
            throw new IllegalArgumentException("Property '"+propertyName+"' must be set for connector");
        }
        return result;
    }

    public String getContext() {
        return readProperty("inbound.context");
    }

    public String getActivationCondition() {
        return readProperty("inbound.activationCondition");
    }
    public String getVariableMapping() {
        return readProperty("inbound.variableMapping");
    }

    // Security / HMAC Validation

    // Dropdown that indicates whether customer wants to validate webhook request with HMAC. Values: enabled | disabled
    public String shouldValidateHMAC() {
        return genericProperties.getProperties().getOrDefault("inbound.shouldValidateHmac", HMACSwitchCustomerChoice.disabled.name());
    }
    // HMAC secret token. An arbitrary String, example 'mySecretToken'
    public String getHMACSecret() {
        return genericProperties.getProperties().get("inbound.hmacSecret");
    }
    // Indicates which header is used to store HMAC signature. Example, X-Hub-Signature-256
    public String getHMACHeader() {
        return genericProperties.getProperties().get("inbound.hmacHeader");
    }
    // Indicates which algorithm was used to produce HMAC signature. Should correlate enum names of io.camunda.connector.inbound.security.signature.HMACAlgoCustomerChoice
    public String getHMACAlgo() {
        return genericProperties.getProperties().get("inbound.hmacAlgorithm");
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
        return "WebhookConnectorProperties-" + genericProperties.toString();
    }
}
