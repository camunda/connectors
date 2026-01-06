/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.classification;

import io.camunda.connector.idp.extraction.request.common.ai.AiProvider;
import io.camunda.connector.idp.extraction.request.common.extraction.ExtractionProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ClassificationRequest(
    @Valid ExtractionProvider extractor,
    @Valid AiProvider ai,
    @Valid @NotNull ClassificationRequestData input) {}
