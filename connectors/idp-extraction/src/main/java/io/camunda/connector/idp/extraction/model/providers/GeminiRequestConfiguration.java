/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers;

import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record GeminiRequestConfiguration(
    @TemplateProperty(group = "configuration", id = "gcpRegion") String region,
    @TemplateProperty(group = "configuration") String projectId,
    @TemplateProperty(group = "configuration") String bucketName,
    @TemplateProperty(group = "configuration") String grounding,
    @TemplateProperty(group = "configuration") String safetySettings) {}
