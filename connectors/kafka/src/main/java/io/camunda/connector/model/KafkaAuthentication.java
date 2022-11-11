/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.impl.ConnectorInputException;
import java.util.Objects;
import java.util.Properties;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;

public class KafkaAuthentication {

  protected static final String SASL_JAAS_CONFIG_VALUE =
      "org.apache.kafka.common.security.plain.PlainLoginModule   required username='%s'   password='%s';";

  protected static final String SECURITY_PROTOCOL_VALUE = "SASL_SSL"; // default value
  protected static final String SASL_MECHANISM_VALUE = "PLAIN"; // default value

  @Secret private String username;
  @Secret private String password;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Properties produceAuthenticationProperties() {
    Properties authProps = new Properties();

    // Both username and password arrived empty thus not setting security config.
    if ((username == null || username.isBlank()) && (password == null || password.isBlank())) {
      return authProps;
    }

    if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
      authProps.put(
          SaslConfigs.SASL_JAAS_CONFIG, String.format(SASL_JAAS_CONFIG_VALUE, username, password));
      authProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SECURITY_PROTOCOL_VALUE);
      authProps.put(SaslConfigs.SASL_MECHANISM, SASL_MECHANISM_VALUE);
    } else {
      throw new ConnectorInputException(
          new RuntimeException("Username / password pair is required"));
    }
    return authProps;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KafkaAuthentication that = (KafkaAuthentication) o;
    return username.equals(that.username) && password.equals(that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password);
  }

  @Override
  public String toString() {
    return "KafkaAuthentication{username='[REDACTED]', password='[REDACTED]'}";
  }
}
