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

public class TextractAnalysisTask implements Callable<GetDocumentAnalysisResponse> {

  private final GetDocumentAnalysisRequest docAnalysisReq;

  private final TextractClient textractClient;

  public TextractAnalysisTask(
      GetDocumentAnalysisRequest documentAnalysisRequest, TextractClient textractClient) {
    this.docAnalysisReq = documentAnalysisRequest;
    this.textractClient = textractClient;
  }

  @Override
  public GetDocumentAnalysisResponse call() {
    return textractClient.getDocumentAnalysis(docAnalysisReq);
  }
}
