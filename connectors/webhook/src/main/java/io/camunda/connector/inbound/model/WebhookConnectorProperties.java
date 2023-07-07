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
import java.util.Optional;
import java.util.function.Function;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.inbound.utils.ObjectMapperSupplier;
import io.camunda.connector.runtime.core.feel.FeelParserWrapper;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class WebhookConnectorProperties {

  private final Map<String, Object> genericProperties;
  private String context;
  private String method;
  private String activationCondition;
  private String variableMapping;
  private String shouldValidateHmac;
  private String hmacSecret;
  private String hmacHeader;
  private String hmacAlgorithm;
  private HMACScope[] hmacScopes;

  public enum AuthorizationType {
    NONE,
    JWT
  }

  private AuthorizationType authorizationType;
  private String jwkUrl;
  private Function<Object, List<String>>
      jwtRoleExpression; // e.g.: if admin = true then ["admin"] else roles
  private List<String> requiredPermissions;

  private FeelEngineWrapper feelEngine;

  public WebhookConnectorProperties(Map<String, Object> properties) {

    // properties contain structure like
    // { "inbound": { "context": "myContext", "method": "POST" } }
    // TODO: Rename properties to avoid this mess. This will require a new version of the connector.
    genericProperties = (Map<String, Object>) properties.get("inbound");

    this.feelEngine = new FeelEngineWrapper();

    this.context = readPropertyRequired("context");

    // If method is not specified - allow all. This will ensure backwards compatibility,
    // and ease up writing custom protocol webhook connectors.
    this.method = readPropertyWithDefault("method", HttpMethods.any.name());

    this.activationCondition = readPropertyNullable("activationCondition");
    this.variableMapping = readPropertyNullable("variableMapping");
    this.shouldValidateHmac = readPropertyWithDefault("shouldValidateHmac", disabled.name());
    this.hmacSecret = readPropertyNullable("hmacSecret");
    this.hmacHeader = readPropertyNullable("hmacHeader");
    this.hmacAlgorithm = readPropertyNullable("hmacAlgorithm");
    this.hmacScopes =
        readPropertyAsTypeWithDefault(
            "hmacScopes", HMACScope[].class, new HMACScope[] {HMACScope.BODY});

    this.authorizationType =
        AuthorizationType.valueOf(
            Optional.ofNullable(readPropertyNullable("inbound.authorizationType"))
                .orElse(AuthorizationType.NONE.toString()));
    this.jwkUrl = readPropertyNullable("inbound.jwt.jwkUrl");
    this.jwtRoleExpression = readFeelFunctionPropertyNullable("inbound.jwt.jwtRoleExpression");
    this.requiredPermissions =
        (List<String>) readParsedFeelObjectPropertyNullable("inbound.jwt.requiredPermissions");
  }

  protected <T> T readPropertyAsTypeWithDefault(
      String propertyName, Class<T> type, T defaultValue) {
    return Optional.ofNullable(readPropertyAsTypeNullable(propertyName, type)).orElse(defaultValue);
  }

  protected <T> T readPropertyAsTypeNullable(String propertyName, Class<T> type) {
    Object parsedExpression =
        FeelParserWrapper.parseIfIsFeelExpressionOrGetOriginal(genericProperties.get(propertyName));
    return ObjectMapperSupplier.getMapperInstance().convertValue(parsedExpression, type);
  }

  protected String readPropertyWithDefault(String propertyName, String defaultValue) {
    return genericProperties.getOrDefault(propertyName, defaultValue).toString();
  }

  protected String readPropertyNullable(String propertyName) {
    var prop = genericProperties.get(propertyName);
    if (prop == null) {
      return null;
    }
    return prop.toString();
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

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
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

  public HMACScope[] getHmacScopes() {
    return hmacScopes;
  }

  public void setHmacScopes(final HMACScope[] hmacScopes) {
    this.hmacScopes = hmacScopes;
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

  public AuthorizationType getAuthorizationType() {
    return authorizationType;
  }

  public void setAuthorizationType(AuthorizationType authorizationType) {
    this.authorizationType = authorizationType;
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
        && Objects.equals(method, that.method)
        && Objects.equals(context, that.context)
        && Objects.equals(activationCondition, that.activationCondition)
        && Objects.equals(variableMapping, that.variableMapping)
        && Objects.equals(shouldValidateHmac, that.shouldValidateHmac)
        && Objects.equals(hmacSecret, that.hmacSecret)
        && Objects.equals(hmacHeader, that.hmacHeader)
        && Objects.equals(hmacAlgorithm, that.hmacAlgorithm)
        && Arrays.equals(hmacScopes, that.hmacScopes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        genericProperties,
        context,
        method,
        activationCondition,
        variableMapping,
        shouldValidateHmac,
        hmacSecret,
        hmacHeader,
        hmacAlgorithm,
        Arrays.hashCode(hmacScopes));
  }

  @Override
  public String toString() {
    return "WebhookConnectorProperties-" + genericProperties.toString();
  }
}
