/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.model.AnalyzeDocumentRequest;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.Document;
import io.camunda.connector.textract.model.TextractRequestData;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncTextractCaller implements TextractCaller<AnalyzeDocumentResult> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SyncTextractCaller.class);

  @Override
  public AnalyzeDocumentResult call(
      TextractRequestData requestData, AmazonTextract textractClient) {
    LOGGER.debug("Starting sync task for document analysis with request data: {}", requestData);

    final Document document = createDocument(requestData);

    final AnalyzeDocumentRequest analyzeDocumentRequest =
        new AnalyzeDocumentRequest()
            .withFeatureTypes(prepareFeatureTypes(requestData))
            .withQueriesConfig(prepareQueryConfig(requestData))
            .withDocument(document);

    return textractClient.analyzeDocument(analyzeDocumentRequest);
  }

  private Document createDocument(TextractRequestData requestData) {
    final Document document = new Document();

    if (Objects.isNull(requestData.document())) {
      return document.withS3Object(prepareS3Obj(requestData));
    }

    byte[] docBytes = requestData.document().asByteArray();
    document.withBytes(ByteBuffer.wrap(docBytes));
    return document;
  }
}
