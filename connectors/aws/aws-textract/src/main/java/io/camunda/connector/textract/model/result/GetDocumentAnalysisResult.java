/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model.result;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisResponse;

/**
 * Connector-owned result of a Textract {@code GetDocumentAnalysis} call (POLLING execution: the
 * {@link io.camunda.connector.textract.caller.PollingTextractCaller} merges every page it polls
 * into a single {@code blocks} list, then maps the merged v2 {@link GetDocumentAnalysisResponse}
 * through this record).
 *
 * <p>See {@link AnalyzeDocumentResult} for why the raw v2 response cannot be returned directly as
 * the connector result.
 */
@JsonPropertyOrder({
  "sdkResponseMetadata",
  "sdkHttpMetadata",
  "documentMetadata",
  "jobStatus",
  "nextToken",
  "blocks",
  "warnings",
  "statusMessage",
  "analyzeDocumentModelVersion"
})
public record GetDocumentAnalysisResult(
    SdkResponseMetadata sdkResponseMetadata,
    SdkHttpMetadata sdkHttpMetadata,
    DocumentMetadata documentMetadata,
    String jobStatus,
    String nextToken,
    List<Block> blocks,
    List<Warning> warnings,
    String statusMessage,
    String analyzeDocumentModelVersion) {

  public static GetDocumentAnalysisResult from(final GetDocumentAnalysisResponse response) {
    return new GetDocumentAnalysisResult(
        SdkResponseMetadata.from(response.responseMetadata()),
        SdkHttpMetadata.from(response.sdkHttpResponse()),
        DocumentMetadata.from(response.documentMetadata()),
        response.jobStatusAsString(),
        response.nextToken(),
        response.hasBlocks() ? mapBlocks(response.blocks()) : null,
        response.hasWarnings() ? mapWarnings(response.warnings()) : null,
        response.statusMessage(),
        response.analyzeDocumentModelVersion());
  }

  private static List<Block> mapBlocks(
      final List<software.amazon.awssdk.services.textract.model.Block> blocks) {
    return blocks.stream().map(Block::from).toList();
  }

  private static List<Warning> mapWarnings(
      final List<software.amazon.awssdk.services.textract.model.Warning> warnings) {
    return warnings.stream().map(Warning::from).toList();
  }
}
