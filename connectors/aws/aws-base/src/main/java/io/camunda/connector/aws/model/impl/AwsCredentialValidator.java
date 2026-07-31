/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model.impl;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.aws.CredentialsProviderSupportV2;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * surfaced. The full exception is available at {@code DEBUG} for operators who need to diagnose a
 * failure — enabling that level is an explicit, deployment-level decision to accept those details
 * in the logs.
 */
public class AwsCredentialValidator implements ConfigurationValidator<AwsCredentialConfiguration> {

  private static final Logger LOG = LoggerFactory.getLogger(AwsCredentialValidator.class);

  /** Set in SaaS deployments; mirrors the detection in {@code AwsBaseRequest}. */
  private static final String SAAS_ENV_VAR = "CAMUNDA_CONNECTOR_RUNTIME_SAAS";

  static final String UNAUTHORIZED_MESSAGE = "AWS rejected the credential (unauthorized).";
  static final String GENERIC_MESSAGE = "The AWS credential could not be validated.";
  static final String MISSING_AUTH_MESSAGE = "Authentication is required.";
  static final String DEFAULT_CHAIN_IN_SAAS_MESSAGE =
      "The default credentials chain is not supported in SaaS.";

  /** Seam for testing: performs the authenticated call, throwing on failure. */
  @FunctionalInterface
  interface IdentityCheck {
    void run(AwsCredentialConfiguration configuration);
  }

  private final IdentityCheck identityCheck;
  private final BooleanSupplier saas;

  public AwsCredentialValidator() {
    this(AwsCredentialValidator::callGetCallerIdentity);
  }

  AwsCredentialValidator(IdentityCheck identityCheck) {
    this(identityCheck, () -> System.getenv().containsKey(SAAS_ENV_VAR));
  }

  AwsCredentialValidator(IdentityCheck identityCheck, BooleanSupplier saas) {
    this.identityCheck = identityCheck;
    this.saas = saas;
  }

  @Override
  public ConfigurationValidationResult validate(AwsCredentialConfiguration configuration) {
    // The only guard against a missing authentication: the configuration record deliberately
    // carries no @NotNull, so nothing upstream rejects it. Without this check it would fall through
    // to the runtime's default credential chain and validate using the runtime's own identity.
    if (configuration.authentication() == null) {
      return ConfigurationValidationResult.failure(ErrorCode.INVALID_INPUT, MISSING_AUTH_MESSAGE);
    }
    // The default credentials chain authenticates as the runtime itself, which in SaaS is Camunda's
    // own identity rather than the customer's. Connector execution rejects it there
    // (AwsBaseRequest#isDefaultCredentialsChainUsedInSaaS), so validating it would report SUCCESS
    // for a credential every SaaS connector will refuse.
    if (configuration.authentication()
            instanceof AwsAuthentication.AwsDefaultCredentialsChainAuthentication
        && saas.getAsBoolean()) {
      return ConfigurationValidationResult.failure(
          ErrorCode.INVALID_INPUT, DEFAULT_CHAIN_IN_SAAS_MESSAGE);
    }
    try {
      identityCheck.run(configuration);
      return ConfigurationValidationResult.success();
    } catch (StsException e) {
      boolean unauthorized = e.statusCode() == 403 || e.statusCode() == 401;
      LOG.debug("AWS rejected the credential (status {})", e.statusCode(), e);
      return unauthorized
          ? ConfigurationValidationResult.failure(ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE)
          : ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
    } catch (Exception e) {
      LOG.debug("AWS credential validation failed", e);
      return ConfigurationValidationResult.failure(ErrorCode.ERROR, GENERIC_MESSAGE);
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
