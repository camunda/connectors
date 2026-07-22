/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model.impl;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.aws.CredentialsProviderSupportV2;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.StsException;

/**
 * Validates an {@link AwsCredentialConfiguration} out-of-band with a generic STS {@code
 * GetCallerIdentity} call — so the check reflects credential validity rather than any single
 * connector's service permissions, and is reused by every AWS connector that consumes the
 * credential.
 *
 * <p>The AWS SDK dependency lives here, not on the configuration record, so the element-template
 * generator can load the record without the runtime SDK on its classpath.
 *
 * <p>Messages returned to the caller are static and value-free: raw AWS SDK exception text can
 * carry endpoints, request ids, profile paths, or credential-identifying detail, so it is never
 * surfaced.
 */
public class AwsCredentialValidator implements ConfigurationValidator<AwsCredentialConfiguration> {

  static final String UNAUTHORIZED_CODE = "UNAUTHORIZED";
  static final String ERROR_CODE = "ERROR";
  static final String INVALID_INPUT_CODE = "INVALID_INPUT";
  static final String UNAUTHORIZED_MESSAGE = "AWS rejected the credential (unauthorized).";
  static final String GENERIC_MESSAGE = "The AWS credential could not be validated.";
  static final String MISSING_AUTH_MESSAGE = "Authentication is required.";

  /** Seam for testing: performs the authenticated call, throwing on failure. */
  @FunctionalInterface
  interface IdentityCheck {
    void run(AwsCredentialConfiguration configuration);
  }

  private final IdentityCheck identityCheck;

  public AwsCredentialValidator() {
    this(AwsCredentialValidator::callGetCallerIdentity);
  }

  AwsCredentialValidator(IdentityCheck identityCheck) {
    this.identityCheck = identityCheck;
  }

  @Override
  public ConfigurationValidationResult validate(AwsCredentialConfiguration configuration) {
    // Defensive: a null authentication would otherwise fall through to the runtime's default
    // credential chain and validate using the runtime's own identity. Normally rejected upstream by
    // @NotNull, but guarded here in case the validator is invoked without prior bean validation.
    if (configuration.authentication() == null) {
      return ConfigurationValidationResult.failure(INVALID_INPUT_CODE, MISSING_AUTH_MESSAGE);
    }
    try {
      identityCheck.run(configuration);
      return ConfigurationValidationResult.success();
    } catch (StsException e) {
      String code = e.statusCode() == 403 || e.statusCode() == 401 ? UNAUTHORIZED_CODE : ERROR_CODE;
      String message = code.equals(UNAUTHORIZED_CODE) ? UNAUTHORIZED_MESSAGE : GENERIC_MESSAGE;
      return ConfigurationValidationResult.failure(code, message);
    } catch (Exception e) {
      return ConfigurationValidationResult.failure(ERROR_CODE, GENERIC_MESSAGE);
    }
  }

  private static void callGetCallerIdentity(AwsCredentialConfiguration configuration) {
    try (StsClient sts =
        StsClient.builder()
            .credentialsProvider(
                CredentialsProviderSupportV2.credentialsProvider(configuration.authentication()))
            .region(Region.of(configuration.region()))
            .build()) {
      sts.getCallerIdentity();
    }
  }
}
