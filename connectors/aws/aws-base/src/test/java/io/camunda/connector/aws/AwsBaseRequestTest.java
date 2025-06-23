/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.connector.aws.model.impl.AwsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class AwsBaseRequestTest {

  @SystemStub private EnvironmentVariables environment;

  @Test
  void shouldReturnTrue_WhenSaaSAndDefaultCredentialChainUsed() {
    AwsBaseRequest request = new AwsBaseRequest();
    request.setAuthentication(new AwsAuthentication.AwsDefaultCredentialsChainAuthentication());
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", null));
    environment.set("CAMUNDA_CONNECTOR_RUNTIME_SAAS", "true");
    assertTrue(request.isDefaultCredentialsChainUsedInSaaS());
  }

  @Test
  void shouldReturnFalse_WhenNotSaaSAndDefaultCredentialChainUsed() {
    AwsBaseRequest request = new AwsBaseRequest();
    request.setAuthentication(new AwsAuthentication.AwsDefaultCredentialsChainAuthentication());
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", null));
    environment.set("CAMUNDA_CONNECTOR_RUNTIME_SAAS", null);
    assertFalse(request.isDefaultCredentialsChainUsedInSaaS());
  }

  @Test
  void shouldReturnFalse_WhenSaaSAndDifferentAuthIsUsed() {
    AwsBaseRequest request = new AwsBaseRequest();
    request.setAuthentication(
        new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", null));
    environment.set("CAMUNDA_CONNECTOR_RUNTIME_SAAS", "true");
    assertFalse(request.isDefaultCredentialsChainUsedInSaaS());
  }

  @Test
  void shouldReturnFalse_WhenNotSaaSOrNotDefaultCredentialChain() {
    AwsBaseRequest request = new AwsBaseRequest();
    request.setAuthentication(
        new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", null));
    environment.set("CAMUNDA_CONNECTOR_RUNTIME_SAAS", null);
    assertFalse(request.isDefaultCredentialsChainUsedInSaaS());
  }
}
