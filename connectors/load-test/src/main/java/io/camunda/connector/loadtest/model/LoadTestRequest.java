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

@TemplateSubType(id = "loadTest", label = "Load Test")
public record LoadTestRequest(
    @TemplateProperty(
            group = "timing",
            label = "I/O wait (ms)",
            description = "Duration in milliseconds to wait (simulates I/O operations)",
            type = PropertyType.Number,
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "ioWaitMs"))
        @Min(0)
        @Max(10_000)
        Long ioWaitMs,
    @TemplateProperty(
            group = "timing",
            label = "CPU burn (ms)",
            description = "Duration in milliseconds to perform CPU-intensive operations",
            type = PropertyType.Number,
            feel = FeelMode.optional,
            binding = @PropertyBinding(name = "cpuBurnMs"))
        @Min(0)
        @Max(10_000)
        Long cpuBurnMs) {}
