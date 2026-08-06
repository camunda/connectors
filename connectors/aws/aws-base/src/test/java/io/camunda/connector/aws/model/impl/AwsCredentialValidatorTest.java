/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sts.model.StsException;

class AwsCredentialValidatorTest {

  private static final AwsCredentialConfiguration VALID =
      new AwsCredentialConfiguration(
          new AwsStaticCredentialsAuthentication("access-key", "secret-key"), "us-east-1");

  @Test
  void successWhenIdentityCheckPasses() {
    var validator = new AwsCredentialValidator(configuration -> {});

    var result = validator.validate(VALID);

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void unauthorizedOn403AndDoesNotLeakSdkMessage() {
    var validator =
        new AwsCredentialValidator(
            configuration -> {
              throw sts(403, "token SENSITIVE-DETAIL is invalid (Request ID: abc)");
            });

    var result = validator.validate(VALID);

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
    assertThat(result.message()).doesNotContain("SENSITIVE-DETAIL");
  }

  @Test
  void unauthorizedOn401() {
    var validator = new AwsCredentialValidator(configuration -> throwSts(401));

    assertThat(validator.validate(VALID).code()).isEqualTo("UNAUTHORIZED");
  }

  @Test
  void errorOnOtherStatusAndDoesNotLeakSdkMessage() {
    var validator =
        new AwsCredentialValidator(configuration -> throwStsWithMessage(500, "boom SENSITIVE"));

    var result = validator.validate(VALID);

    assertThat(result.code()).isEqualTo("ERROR");
    assertThat(result.message()).doesNotContain("SENSITIVE");
  }

  @Test
  void errorOnNonSdkExceptionAndDoesNotLeakMessage() {
    var validator =
        new AwsCredentialValidator(
            configuration -> {
              throw new RuntimeException("boom SENSITIVE");
            });

    var result = validator.validate(VALID);

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("ERROR");
    assertThat(result.message()).doesNotContain("SENSITIVE");
  }

  @Test
  void rejectsMissingAuthenticationWithoutCallingAws() {
    var called = new boolean[] {false};
    var validator = new AwsCredentialValidator(configuration -> called[0] = true);

    var result = validator.validate(new AwsCredentialConfiguration(null, "us-east-1"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("INVALID_INPUT");
    assertThat(called[0]).isFalse();
  }

  @Test
  void rejectsDefaultCredentialsChainInSaasWithoutCallingAws() {
    // In SaaS the chain would authenticate as Camunda's own runtime identity, and connector
    // execution rejects it there — so it must never be reported as a usable credential.
    var called = new boolean[] {false};
    var validator = new AwsCredentialValidator(configuration -> called[0] = true, () -> true);

    var result =
        validator.validate(
            new AwsCredentialConfiguration(
                new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(), "us-east-1"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("INVALID_INPUT");
    assertThat(called[0]).isFalse();
  }

  @Test
  void allowsDefaultCredentialsChainOutsideSaas() {
    // Hybrid/Self-Managed run as the customer's own identity, where the chain is a valid choice.
    var validator = new AwsCredentialValidator(configuration -> {}, () -> false);

    var result =
        validator.validate(
            new AwsCredentialConfiguration(
                new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(), "us-east-1"));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void staticCredentialsAreUnaffectedBySaas() {
    var validator = new AwsCredentialValidator(configuration -> {}, () -> true);

    assertThat(validator.validate(VALID).status()).isEqualTo(Status.SUCCESS);
  }

  private static void throwSts(int status) {
    throw sts(status, "message");
  }

  private static void throwStsWithMessage(int status, String message) {
    throw sts(status, message);
  }

  private static StsException sts(int status, String message) {
    return (StsException) StsException.builder().statusCode(status).message(message).build();
  }
}
