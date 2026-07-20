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
import io.camunda.connector.aws.model.impl.AwsCredentialConfiguration;
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

  /**
   * When both an inline authentication/region and a bound credential are set, the credential must
   * win for authentication and region, while the inline endpoint (which the credential has no
   * equivalent for) is preserved.
   */
  @Test
  void credentialTakesPrecedenceOverInlineWhilePreservingInlineEndpoint() {
    AwsBaseRequest request = new AwsBaseRequest();
    request.setAuthentication(
        new AwsAuthentication.AwsStaticCredentialsAuthentication("inline-key", "inline-secret"));
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", "https://inline-endpoint"));
    request.setAwsCredential(
        new AwsCredentialConfiguration(
            new AwsAuthentication.AwsStaticCredentialsAuthentication(
                "credential-key", "credential-secret"),
            "us-east-1"));

    assertEquals(
        new AwsAuthentication.AwsStaticCredentialsAuthentication(
            "credential-key", "credential-secret"),
        request.getAuthentication());
    assertEquals("us-east-1", request.getConfiguration().region());
    assertEquals("https://inline-endpoint", request.getConfiguration().endpoint());
  }
}
