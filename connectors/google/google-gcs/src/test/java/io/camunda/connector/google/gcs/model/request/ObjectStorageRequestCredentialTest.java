/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ObjectStorageRequestCredentialTest {

  private static Authentication authentication(String jsonKey) {
    Authentication authentication = new Authentication();
    authentication.setJsonKey(jsonKey);
    return authentication;
  }

  private static GcsCredentialConfiguration credential(String jsonKey) {
    return new GcsCredentialConfiguration(authentication(jsonKey));
  }

  @Test
  void credentialBound_noInline_usesCredentialAuthentication() {
    ObjectStorageRequest request = new ObjectStorageRequest();
    request.setGoogleGcsCredential(credential("from-credential"));

    assertThat(request.getAuthentication().getJsonKey()).isEqualTo("from-credential");
    assertThat(request.isAuthenticationPresent()).isTrue();
  }

  @Test
  void noCredential_inlinePresent_usesInlineAuthentication() {
    ObjectStorageRequest request = new ObjectStorageRequest();
    request.setAuthentication(authentication("from-inline"));

    assertThat(request.getAuthentication().getJsonKey()).isEqualTo("from-inline");
    assertThat(request.isAuthenticationPresent()).isTrue();
  }

  @Test
  void bothPresent_credentialWins() {
    ObjectStorageRequest request = new ObjectStorageRequest();
    request.setGoogleGcsCredential(credential("from-credential"));
    request.setAuthentication(authentication("from-inline"));

    assertThat(request.getAuthentication().getJsonKey()).isEqualTo("from-credential");
  }

  @Test
  void neitherPresent_isAuthenticationNotPresent() {
    ObjectStorageRequest request = new ObjectStorageRequest();

    assertThat(request.getAuthentication()).isNull();
    assertThat(request.isAuthenticationPresent()).isFalse();
  }

  @Test
  void credentialBound_inlineNotValidatedWhenCredentialWins() {
    ObjectStorageRequest request = new ObjectStorageRequest();
    request.setGoogleGcsCredential(credential("from-credential"));
    request.setAuthentication(authentication("from-inline"));

    assertThat(request.getInlineAuthenticationWhenNoCredentialBound()).isNull();
  }
}
