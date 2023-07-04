/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.disabled;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.impl.inbound.ProcessCorrelationPoint;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.feel.FeelParserWrapper;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class WebhookConnectorProperties {

  private final InboundConnectorProperties genericProperties;
  @Secret private String context;
  private String activationCondition;
  private String variableMapping;
  private String shouldValidateHmac;
  @Secret private String hmacSecret;
  @Secret private String hmacHeader;
  private String hmacAlgorithm;
  private String jwkUrl;
  private Function<Object, List<String>>
      jwtRoleExpression; // e.g.: if admin = true then ["admin"] else roles
  private List<String> requiredPermissions;

  private FeelEngineWrapper feelEngine;

  public WebhookConnectorProperties(InboundConnectorProperties properties) {
    this.feelEngine = new FeelEngineWrapper();
    this.genericProperties = properties;

    this.context = readPropertyRequired("inbound.context");
    this.activationCondition = readPropertyNullable("inbound.activationCondition");
    this.variableMapping = readPropertyNullable("inbound.variableMapping");
    this.shouldValidateHmac =
        readPropertyWithDefault("inbound.shouldValidateHmac", disabled.name());
    this.hmacSecret = readPropertyNullable("inbound.hmacSecret");
    this.hmacHeader = readPropertyNullable("inbound.hmacHeader");
    this.hmacAlgorithm = readPropertyNullable("inbound.hmacAlgorithm");
    this.jwkUrl = readPropertyNullable("inbound.jwkUrl");
    this.jwtRoleExpression = readFeelFunctionPropertyNullable("inbound.jwtRoleExpression");
    this.requiredPermissions =
        (List<String>) readParsedFeelObjectPropertyNullable("inbound.requiredPermissions");
  }

  public String getConnectorIdentifier() {
    return getContext()
        + "-"
        + genericProperties.getBpmnProcessId()
        + "-"
        + genericProperties.getVersion();
  }

  protected String readPropertyWithDefault(String propertyName, String defaultValue) {
    return genericProperties.getProperties().getOrDefault(propertyName, defaultValue);
  }

  protected String readPropertyNullable(String propertyName) {
    return genericProperties.getProperties().get(propertyName);
  }

  protected Object readParsedFeelObjectPropertyNullable(String propertyName) {
    return FeelParserWrapper.parseIfIsFeelExpressionOrGetOriginal(
        genericProperties.getProperties().get(propertyName));
  }

  protected Function<Object, List<String>> readFeelFunctionPropertyNullable(String propertyName) {
    String rawFeelExpression = genericProperties.getProperties().get(propertyName);
    if (!FeelParserWrapper.isFeelExpression(rawFeelExpression)) {
      throw new IllegalArgumentException(propertyName + " should be a FEEL expression!");
    }
    return variables -> this.feelEngine.evaluate(rawFeelExpression, variables);
  }

  protected String readPropertyRequired(String propertyName) {
    String result = readPropertyNullable(propertyName);
    if (result == null) {
      throw new IllegalArgumentException(
          "Property '" + propertyName + "' must be set for connector");
    }
    return result;
  }

  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
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

  // Dropdown that indicates whether customer wants to validate webhook request with HMAC. Values:
  // enabled | disabled
  public String getShouldValidateHmac() {
    return shouldValidateHmac;
  }

  public void setShouldValidateHmac(String shouldValidateHmac) {
    this.shouldValidateHmac = shouldValidateHmac;
  }

  // HMAC secret token. An arbitrary String, example 'mySecretToken'
  public String getHmacSecret() {
    return hmacSecret;
  }

  public void setHmacSecret(String hmacSecret) {
    this.hmacSecret = hmacSecret;
  }

  // Indicates which header is used to store HMAC signature. Example, X-Hub-Signature-256
  public String getHmacHeader() {
    return hmacHeader;
  }

  public void setHmacHeader(String hmacHeader) {
    this.hmacHeader = hmacHeader;
  }

  // Indicates which algorithm was used to produce HMAC signature. Should correlate enum names of
  // io.camunda.connector.inbound.security.signature.HMACAlgoCustomerChoice
  public String getHmacAlgorithm() {
    return hmacAlgorithm;
  }

  public void setHmacAlgorithm(String hmacAlgorithm) {
    this.hmacAlgorithm = hmacAlgorithm;
  }

  public String getType() {
    return genericProperties.getType();
  }

  public ProcessCorrelationPoint getCorrelationPoint() {
    return genericProperties.getCorrelationPoint();
  }

  public String getBpmnProcessId() {
    return genericProperties.getBpmnProcessId();
  }

  public int getProcessDefinitionVersion() {
    return genericProperties.getVersion();
  }

  public long getProcessDefinitionKey() {
    return genericProperties.getProcessDefinitionKey();
  }

  public String getJwkUrl() {
    return jwkUrl;
  }

  public void setJwkUrl(String jwkUrl) {
    this.jwkUrl = jwkUrl;
  }

  public Function<Object, List<String>> getJwtRoleExpression() {
    return jwtRoleExpression;
  }

  public void setJwtRoleExpression(Function<Object, List<String>> jwtRoleExpression) {
    this.jwtRoleExpression = jwtRoleExpression;
  }

  public List<String> getRequiredPermissions() {
    return requiredPermissions;
  }

  public void setRequiredPermissions(List<String> requiredPermissions) {
    this.requiredPermissions = requiredPermissions;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WebhookConnectorProperties that = (WebhookConnectorProperties) o;
    return Objects.equals(genericProperties, that.genericProperties)
        && Objects.equals(context, that.context)
        && Objects.equals(activationCondition, that.activationCondition)
        && Objects.equals(variableMapping, that.variableMapping)
        && Objects.equals(shouldValidateHmac, that.shouldValidateHmac)
        && Objects.equals(hmacSecret, that.hmacSecret)
        && Objects.equals(hmacHeader, that.hmacHeader)
        && Objects.equals(hmacAlgorithm, that.hmacAlgorithm);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        genericProperties,
        context,
        activationCondition,
        variableMapping,
        shouldValidateHmac,
        hmacSecret,
        hmacHeader,
        hmacAlgorithm);
  }

  @Override
  public String toString() {
    return "WebhookConnectorProperties-" + genericProperties.toString();
  }
}
