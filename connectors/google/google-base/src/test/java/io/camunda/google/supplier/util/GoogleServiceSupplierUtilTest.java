/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google.supplier.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.GoogleCredentials;
import io.camunda.google.model.Authentication;
import io.camunda.google.model.AuthenticationType;
import java.io.IOException;
import java.util.Date;
import org.junit.jupiter.api.Test;

class GoogleServiceSupplierUtilTest {

  @Test
  void bearerCredentials_doNotAttemptRefresh() throws IOException {
    var auth = new Authentication(AuthenticationType.BEARER, "token", null, null, null);

    var credentials = (GoogleCredentials) GoogleServiceSupplierUtil.getCredentials(auth);
    credentials.refreshIfExpired();

    assertThat(credentials.getAccessToken().getTokenValue()).isEqualTo("token");
    assertThat(credentials.getAccessToken().getExpirationTime()).isAfter(new Date());
  }

  @Test
  void bearerAdapter_doesNotAttemptRefresh() throws IOException {
    var auth = new Authentication(AuthenticationType.BEARER, "token", null, null, null);

    var adapter = GoogleServiceSupplierUtil.getHttpHttpCredentialsAdapter(auth);

    assertThat(adapter.getCredentials().getRequestMetadata().get("Authorization"))
        .containsExactly("Bearer token");
  }
}
