/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.memory.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.aws.model.impl.AwsCredentialConfiguration;
import org.junit.jupiter.api.Test;

/** Verifies the per-connector consumption of a bound AWS credential (configuration). */
class AgentCoreMemoryRequestTest {

  private static final AwsCredentialConfiguration CREDENTIAL =
      new AwsCredentialConfiguration(
          new AwsStaticCredentialsAuthentication("cred-ak", "cred-sk"), "eu-west-1");

  @Test
  void usesCredentialAuthenticationAndRegionWhenBound() {
    var request = new AgentCoreMemoryRequest();
    request.setAwsCredential(CREDENTIAL);

    assertThat(request.getAuthentication()).isInstanceOf(AwsStaticCredentialsAuthentication.class);
    assertThat(((AwsStaticCredentialsAuthentication) request.getAuthentication()).accessKey())
        .isEqualTo("cred-ak");
    assertThat(request.getConfiguration().region()).isEqualTo("eu-west-1");
  }

  @Test
  void fallsBackToInlineWhenNoCredential() {
    var request = new AgentCoreMemoryRequest();
    request.setAuthentication(new AwsStaticCredentialsAuthentication("inline-ak", "inline-sk"));
    request.setConfiguration(new AwsBaseConfiguration("us-east-1", null));

    assertThat(((AwsStaticCredentialsAuthentication) request.getAuthentication()).accessKey())
        .isEqualTo("inline-ak");
    assertThat(request.getConfiguration().region()).isEqualTo("us-east-1");
  }

  @Test
  void credentialOnlySatisfiesAuthenticationValidation() {
    // No inline authentication set; validation is getter-based, so a bound credential satisfies it.
    var request = new AgentCoreMemoryRequest();
    request.setAwsCredential(CREDENTIAL);

    assertThat(request.isAuthenticationPresent()).isTrue();
  }
}
