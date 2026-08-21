/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.connector.aws.model.impl.AwsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.aws.model.impl.AwsCredentialConfiguration;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
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

  /**
   * Reproduces the actual Modeler-generated shape for a credential-only diagram: {@code
   * authentication.type} is an unconditional zeebe:input with a static default ({@code
   * "credentials"}), so it is always present even though the user never filled in the
   * (conditionally hidden) accessKey/secretKey. This must not fail validation now that
   * awsCredential is bound and takes precedence.
   */
  @Test
  void validationSucceedsWithCredentialBoundDespiteUnconditionalInlineDiscriminator() {
    AwsBaseRequest request = new AwsBaseRequest();
    request.setAwsCredential(
        new AwsCredentialConfiguration(
            new AwsAuthentication.AwsStaticCredentialsAuthentication(
                "credential-key", "credential-secret"),
            "us-east-1"));
    request.setAuthentication(new AwsAuthentication.AwsStaticCredentialsAuthentication(null, null));

    assertThatCode(() -> new DefaultValidationProvider().validate(request)).doesNotThrowAnyException();
  }

  /** Without a bound credential, the same incomplete inline authentication must still fail. */
  @Test
  void validationFailsWithIncompleteInlineAuthenticationWhenNoCredentialBound() {
    AwsBaseRequest request = new AwsBaseRequest();
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", null));
    request.setAuthentication(new AwsAuthentication.AwsStaticCredentialsAuthentication(null, null));

    assertThrows(
        Exception.class, () -> new DefaultValidationProvider().validate(request));
  }
}
