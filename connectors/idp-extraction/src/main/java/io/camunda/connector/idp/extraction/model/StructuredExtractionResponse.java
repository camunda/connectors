/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import java.util.Map;

public record StructuredExtractionResponse(
    Map<String, Object> extractedFields,
    Map<String, Object> confidenceScore,
    Map<String, Polygon> geometry) {}
