/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import io.camunda.connector.textract.model.TextractRequestData;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentResponse;
import software.amazon.awssdk.services.textract.model.Document;

public class SyncTextractCaller implements TextractCaller<AnalyzeDocumentResponse> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SyncTextractCaller.class);

  @Override
  public AnalyzeDocumentResponse call(
      TextractRequestData requestData, TextractClient textractClient) {
    LOGGER.debug("Starting sync task for document analysis with request data: {}", requestData);

    final Document document = createDocument(requestData);

    final AnalyzeDocumentRequest analyzeDocumentRequest =
        AnalyzeDocumentRequest.builder()
            .featureTypes(prepareFeatureTypes(requestData))
            .queriesConfig(prepareQueryConfig(requestData))
            .document(document)
        .build();

    return textractClient.analyzeDocument(analyzeDocumentRequest);
  }

  private Document createDocument(TextractRequestData requestData) {
    final Document document = Document.builder()
        .build();

    if (Objects.isNull(requestData.document())) {
      return document.s3Object(prepareS3Obj(requestData));
    }

    byte[] docBytes = requestData.document().asByteArray();
    document.bytes(ByteBuffer.wrap(docBytes));
    return document;
  }
}
