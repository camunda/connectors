/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model.result;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentResponse;

/**
 * Connector-owned result of a Textract {@code AnalyzeDocument} call (SYNC execution).
 *
 * <p>The AWS SDK v2 model classes ({@link AnalyzeDocumentResponse} and friends) expose fluent
 * accessors ({@code blocks()}, {@code documentMetadata()}, ...) rather than JavaBean getters.
 * Serializing them directly with the connectors' {@code ObjectMapper} (which disables {@code
 * FAIL_ON_EMPTY_BEANS}) would silently produce {@code {}}, dropping every extracted block (see
 * #7967 / #7977 for the same hazard already fixed in aws-eventbridge). This record maps the v2
 * response back into the exact JSON shape that the pre-v2 (AWS SDK v1) connector documented and
 * returned, restoring that output contract.
 */
@JsonPropertyOrder({
  "sdkResponseMetadata",
  "sdkHttpMetadata",
  "documentMetadata",
  "blocks",
  "humanLoopActivationOutput",
  "analyzeDocumentModelVersion"
})
public record AnalyzeDocumentResult(
    SdkResponseMetadata sdkResponseMetadata,
    SdkHttpMetadata sdkHttpMetadata,
    DocumentMetadata documentMetadata,
    List<Block> blocks,
    HumanLoopActivationOutput humanLoopActivationOutput,
    String analyzeDocumentModelVersion) {

  public static AnalyzeDocumentResult from(final AnalyzeDocumentResponse response) {
    return new AnalyzeDocumentResult(
        SdkResponseMetadata.from(response.responseMetadata()),
        SdkHttpMetadata.from(response.sdkHttpResponse()),
        DocumentMetadata.from(response.documentMetadata()),
        response.hasBlocks() ? mapBlocks(response.blocks()) : null,
        HumanLoopActivationOutput.from(response.humanLoopActivationOutput()),
        response.analyzeDocumentModelVersion());
  }

  private static List<Block> mapBlocks(
      final List<software.amazon.awssdk.services.textract.model.Block> blocks) {
    return blocks.stream().map(Block::from).toList();
  }
}
