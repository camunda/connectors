/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import org.junit.jupiter.api.Test;

class RestAuthenticationConfigurationTest {

  @Test
  void refreshTokenCredentialPayloadUsesFlatRuntimeFields() throws Exception {
    var configuration =
        ConnectorsObjectMapperSupplier.getCopy()
            .readValue(
                """
                {
                  "authentication": {
                    "type": "oauth-refresh-token",
                    "oauthTokenEndpoint": "https://example.com/oauth/token",
                    "clientId": "client-id",
                    "clientSecret": "client-secret",
                    "refreshToken": "refresh-token",
                    "scopes": "openid offline_access"
                  }
                }
                """,
                RestAuthenticationConfiguration.class);

    ValidationUtil.discoverDefaultValidationProviderImplementation().validate(configuration);

    assertThat(configuration.authentication())
        .isEqualTo(
            new OAuthRefreshTokenAuthentication(
                "https://example.com/oauth/token",
                "client-id",
                "client-secret",
                "refresh-token",
                "openid offline_access"));
    assertThat(configuration.url()).isNull();
  }
}
