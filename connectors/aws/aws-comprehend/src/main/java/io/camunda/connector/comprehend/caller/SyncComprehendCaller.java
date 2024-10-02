/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import com.amazonaws.services.comprehend.AmazonComprehendClient;
import com.amazonaws.services.comprehend.model.ClassifyDocumentRequest;
import com.amazonaws.services.comprehend.model.ClassifyDocumentResult;
import io.camunda.connector.comprehend.model.ComprehendSyncRequestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncComprehendCaller
    implements ComprehendCaller<ClassifyDocumentResult, ComprehendSyncRequestData> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SyncComprehendCaller.class);

  @Override
  public ClassifyDocumentResult call(
      AmazonComprehendClient client, ComprehendSyncRequestData requestData) {
    LOGGER.debug(
        "Starting sync comprehend task for document classification with request data: {}",
        requestData);
    ClassifyDocumentRequest classifyDocumentRequest =
        new ClassifyDocumentRequest()
            .withText(requestData.text())
            .withEndpointArn(requestData.endpointArn());

    return client.classifyDocument(classifyDocumentRequest);
  }
}
