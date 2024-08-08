/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model;

import com.amazonaws.services.textract.AmazonTextractAsync;
import com.amazonaws.services.textract.model.GetDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import java.util.concurrent.Callable;

public class TextractTask implements Callable<GetDocumentAnalysisResult> {

  private final GetDocumentAnalysisRequest docAnalysisReq;

  private final AmazonTextractAsync amazonTextract;

  public TextractTask(
      GetDocumentAnalysisRequest documentAnalysisRequest, AmazonTextractAsync amazonTextract) {
    this.docAnalysisReq = documentAnalysisRequest;
    this.amazonTextract = amazonTextract;
  }

  @Override
  public GetDocumentAnalysisResult call() {
    return this.amazonTextract.getDocumentAnalysis(docAnalysisReq);
  }
}
