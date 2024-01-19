/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model.impl;

import io.camunda.connector.aws.model.AwsConfiguration;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record AwsBaseConfiguration(
    @TemplateProperty(
            group = "configuration",
            description = "Specify the AWS region",
            optional = true)
        String region,
    @TemplateProperty(
            group = "configuration",
            description = "Specify endpoint if need to use custom endpoint",
            type = TemplateProperty.PropertyType.Hidden,
            feel = Property.FeelMode.disabled,
            optional = true)
        String endpoint)
    implements AwsConfiguration {}
