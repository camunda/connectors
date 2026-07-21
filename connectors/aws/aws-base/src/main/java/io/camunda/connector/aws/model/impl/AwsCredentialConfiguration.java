/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model.impl;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.aws.CredentialsProviderSupportV2;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.StsException;

/**
 * Configuration (credential) template for reusable AWS credentials, shared by AWS connectors.
 * Reuses the existing sealed {@link AwsAuthentication} (a discriminated union of static credentials
 * vs. the default credentials chain) plus the region, demonstrating that a discriminated auth model
 * maps onto the configuration-template format (a discriminator dropdown with conditional fields).
 *
 * <p>Implements {@link ConfigurationValidator}: the credential validates itself with a generic STS
 * {@code GetCallerIdentity} call, so the check reflects credential validity rather than any single
 * connector's service permissions, and is reused by every AWS connector that consumes it.
 */
@Configuration(id = "io.camunda:aws-credential:1", version = 1, name = "AWS Credential")
public record AwsCredentialConfiguration(
        @Valid @TemplateProperty(group = "authentication") AwsAuthentication authentication,
        @NotBlank
        @TemplateProperty(
                group = "configuration",
                label = "Region",
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String region) implements ConfigurationValidator {

    @Override
    public ConfigurationValidationResult validate() {
        try (StsClient sts =
                     StsClient.builder()
                             .credentialsProvider(CredentialsProviderSupportV2.credentialsProvider(authentication))
                             .region(Region.of(region))
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
