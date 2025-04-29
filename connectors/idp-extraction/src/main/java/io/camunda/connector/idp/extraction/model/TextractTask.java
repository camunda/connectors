/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import java.util.concurrent.Callable;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisResponse;
import software.amazon.awssdk.services.textract.model.GetDocumentTextDetectionRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentTextDetectionResponse;

public class TextractTask implements Callable<GetDocumentTextDetectionResponse> {

  private final GetDocumentTextDetectionRequest docAnalysisReq;

  private final TextractClient textractClient;

  public TextractTask(
          GetDocumentTextDetectionRequest documentAnalysisRequest, TextractClient textractClient) {
    this.docAnalysisReq = documentAnalysisRequest;
    this.textractClient = textractClient;
  }

  @Override
  public GetDocumentTextDetectionResponse call() {
    return textractClient.getDocumentTextDetection(docAnalysisReq);
  }
}
