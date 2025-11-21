/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.loadtest.model;

import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@TemplateSubType(id = "randomLoadTest", label = "Random Load Test")
public record RandomLoadTestRequest(
    @TemplateProperty(
            group = "timing",
            label = "Min I/O wait (ms)",
            description = "Minimum duration in milliseconds to wait (simulates I/O operations)",
            type = PropertyType.Number,
            feel = FeelMode.optional,
            optional = true,
            binding = @PropertyBinding(name = "minIoWaitMs"))
        @Min(0)
        @Max(10_000)
        Long minIoWaitMs,
    @TemplateProperty(
            group = "timing",
            label = "Max I/O wait (ms)",
            description = "Maximum duration in milliseconds to wait (simulates I/O operations)",
            type = PropertyType.Number,
            feel = FeelMode.optional,
            optional = true,
            binding = @PropertyBinding(name = "maxIoWaitMs"))
        @Min(0)
        @Max(10_000)
        Long maxIoWaitMs,
    @TemplateProperty(
            group = "timing",
            label = "Min CPU burn (ms)",
            description = "Minimum duration in milliseconds to perform CPU-intensive operations",
            type = PropertyType.Number,
            feel = FeelMode.optional,
            optional = true,
            binding = @PropertyBinding(name = "minCpuBurnMs"))
        @Min(0)
        @Max(10_000)
        Long minCpuBurnMs,
    @TemplateProperty(
            group = "timing",
            label = "Max CPU burn (ms)",
            description = "Maximum duration in milliseconds to perform CPU-intensive operations",
            type = PropertyType.Number,
            feel = FeelMode.optional,
            optional = true,
            binding = @PropertyBinding(name = "maxCpuBurnMs"))
        @Min(0)
        @Max(10_000)
        Long maxCpuBurnMs) {}
