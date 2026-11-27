/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.auth.oauth2.ServiceAccountCredentials;
import io.camunda.connector.idp.extraction.model.providers.GcpAuthentication;
import io.camunda.connector.idp.extraction.model.providers.GcpAuthenticationType;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class GcsUtilTest {

  @Test
  void shouldAcceptServiceAccountJson() throws Exception {
    var keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    String pem =
        "-----BEGIN PRIVATE KEY-----\\n"
            + Base64.getEncoder().encodeToString(keyGen.generateKeyPair().getPrivate().getEncoded())
            + "\\n-----END PRIVATE KEY-----\\n";
    String json =
        """
        {
          "type": "service_account",
          "project_id": "test-project",
          "private_key_id": "key-id",
          "private_key": "%s",
          "client_email": "sa@test-project.iam.gserviceaccount.com",
          "client_id": "123"
        }
        """
            .formatted(pem);

    var credentials =
        GcsUtil.getCredentials(
            new GcpAuthentication(GcpAuthenticationType.SERVICE_ACCOUNT, null, null, null, null, json));

    assertThat(credentials).isInstanceOf(ServiceAccountCredentials.class);
    assertThat(((ServiceAccountCredentials) credentials).getClientEmail())
        .isEqualTo("sa@test-project.iam.gserviceaccount.com");
  }

  @Test
  void shouldRejectExternalAccountJson() {
    String json =
        """
        {
          "type": "external_account",
          "audience": "//iam.googleapis.com/projects/0/locations/global/workloadIdentityPools/p/providers/p",
          "subject_token_type": "urn:ietf:params:oauth:token-type:jwt",
          "token_url": "https://attacker.example/token",
          "credential_source": { "file": "/etc/hostname" }
        }
        """;

    assertThatThrownBy(() ->
            GcsUtil.getCredentials(
                new GcpAuthentication(
                    GcpAuthenticationType.SERVICE_ACCOUNT, null, null, null, null, json)))
        .rootCause()
        .isInstanceOf(IOException.class)
        .hasMessageContaining("external_account");
  }
}
