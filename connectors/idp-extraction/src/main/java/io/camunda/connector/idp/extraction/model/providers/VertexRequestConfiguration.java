/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers;

import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record VertexRequestConfiguration(
    @TemplateProperty(group = "configuration", id = "gcpRegion", label = "Region") String region,
    @TemplateProperty(group = "configuration", label = "Project ID") String projectId,
    @TemplateProperty(
            group = "configuration",
            label = "Bucket name",
            description =
                "The Google Cloud Storage bucket where the document will be temporarily stored")
        String bucketName) {}
