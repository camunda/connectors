/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.common.ai;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "bedrockAi", label = "AWS Bedrock AI")
public record BedrockAiRequest(
    @TemplateProperty(
            id = "awsAuthType",
            label = "Authentication type",
            group = "ai",
            type = Dropdown,
            defaultValue = "credentials",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Credentials",
                  value = "credentials"),
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Default Credentials Chain (Hybrid/Self-Managed only)",
                  value = "defaultCredentialsChain")
            })
        String awsAuthType,
    @TemplateProperty(
            id = "accessKey",
            label = "Access key",
            description =
                "Provide an IAM access key tailored to a user, equipped with the necessary permissions",
            group = "ai",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "ai.awsAuthType",
                    equals = "credentials"))
        String accessKey,
    @TemplateProperty(
            id = "secretKey",
            label = "Secret key",
            description =
                "Provide a secret key of a user with permissions to invoke specified AWS Lambda function",
            group = "ai",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "ai.awsAuthType",
                    equals = "credentials"))
        String secretKey,
    @TemplateProperty(
            id = "region",
            group = "ai",
            description = "Specify the AWS region",
            type = TemplateProperty.PropertyType.Text,
            feel = FeelMode.disabled,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        @NotNull
        String region)
    implements AiProvider {}
