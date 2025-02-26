/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record ProviderConfiguration(
    @TemplateProperty(group = "awsRequest", type = TemplateProperty.PropertyType.Hidden)
        AwsBaseRequest awsRequest,
    @TemplateProperty(group = "geminiRequest", type = TemplateProperty.PropertyType.Hidden)
        GeminiBaseRequest geminiRequest) {}
