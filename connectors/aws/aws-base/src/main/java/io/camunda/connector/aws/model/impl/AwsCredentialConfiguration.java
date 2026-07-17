/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model.impl;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;

/**
 * Configuration (credential) template for reusable AWS credentials, shared by AWS connectors.
 * Reuses the existing sealed {@link AwsAuthentication} (a discriminated union of static credentials
 * vs. the default credentials chain) plus the region, demonstrating that a discriminated auth model
 * maps onto the configuration-template format (a discriminator dropdown with conditional fields).
 */
@Configuration(id = "io.camunda:aws-credential:1", version = 1, name = "AWS Credential")
public record AwsCredentialConfiguration(
    @Valid @TemplateProperty(group = "authentication") AwsAuthentication authentication,
    @TemplateProperty(group = "configuration", label = "Region") String region) {}
