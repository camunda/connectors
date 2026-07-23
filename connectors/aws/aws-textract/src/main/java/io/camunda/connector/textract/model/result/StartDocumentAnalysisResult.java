/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model.result;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisResponse;

/**
 * Connector-owned result of a Textract {@code StartDocumentAnalysis} call (ASYNC execution: a
 * fire-and-forget job kickoff, no polling).
 *
 * <p>See {@link AnalyzeDocumentResult} for why the raw v2 {@link StartDocumentAnalysisResponse}
 * cannot be returned directly as the connector result.
 */
@JsonPropertyOrder({"sdkResponseMetadata", "sdkHttpMetadata", "jobId"})
public record StartDocumentAnalysisResult(
    SdkResponseMetadata sdkResponseMetadata, SdkHttpMetadata sdkHttpMetadata, String jobId) {

  public static StartDocumentAnalysisResult from(final StartDocumentAnalysisResponse response) {
    return new StartDocumentAnalysisResult(
        SdkResponseMetadata.from(response.responseMetadata()),
        SdkHttpMetadata.from(response.sdkHttpResponse()),
        response.jobId());
  }
}
