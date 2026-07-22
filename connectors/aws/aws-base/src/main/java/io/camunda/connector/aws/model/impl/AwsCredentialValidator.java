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
 */
public class AwsCredentialValidator implements ConfigurationValidator<AwsCredentialConfiguration> {

  @Override
  public ConfigurationValidationResult validate(AwsCredentialConfiguration configuration) {
    try (StsClient sts =
        StsClient.builder()
            .credentialsProvider(
                CredentialsProviderSupportV2.credentialsProvider(configuration.authentication()))
            .region(Region.of(configuration.region()))
            .build()) {
      sts.getCallerIdentity();
      return ConfigurationValidationResult.success();
    } catch (StsException e) {
      String code = e.statusCode() == 403 || e.statusCode() == 401 ? "UNAUTHORIZED" : "ERROR";
      return ConfigurationValidationResult.failure(code, e.getMessage());
    } catch (Exception e) {
      return ConfigurationValidationResult.failure("ERROR", e.getMessage());
    }
  }
}
