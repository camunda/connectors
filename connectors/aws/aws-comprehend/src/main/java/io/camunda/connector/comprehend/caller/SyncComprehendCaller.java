/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import io.camunda.connector.comprehend.ComprehendClassifyResult;
import io.camunda.connector.comprehend.model.ComprehendSyncRequestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.ClassifyDocumentRequest;

public class SyncComprehendCaller
    implements ComprehendCaller<
        ComprehendClient, ComprehendSyncRequestData, ComprehendClassifyResult> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SyncComprehendCaller.class);

  @Override
  public ComprehendClassifyResult call(
      ComprehendClient client, ComprehendSyncRequestData requestData) {
    LOGGER.debug(
        "Starting sync comprehend task for document classification with request data: {}",
        requestData);
    ClassifyDocumentRequest classifyDocumentRequest =
        ClassifyDocumentRequest.builder()
            .text(requestData.text())
            .endpointArn(requestData.endpointArn())
            .build();

    return ComprehendClassifyResult.from(client.classifyDocument(classifyDocumentRequest));
  }
}
