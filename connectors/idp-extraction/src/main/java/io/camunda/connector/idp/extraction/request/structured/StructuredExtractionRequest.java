/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.structured;

import io.camunda.connector.idp.extraction.request.common.extraction.StructuredExtractionProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record StructuredExtractionRequest(
    @Valid @NotNull StructuredExtractionProvider extractor,
    @Valid @NotNull StructuredExtractionRequestData input) {}
