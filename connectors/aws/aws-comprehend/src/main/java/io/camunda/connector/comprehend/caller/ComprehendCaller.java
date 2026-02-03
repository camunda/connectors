/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import com.amazonaws.AmazonWebServiceResult;
import com.amazonaws.ResponseMetadata;
import io.camunda.connector.comprehend.model.ComprehendRequestData;
import software.amazon.awssdk.services.comprehend.ComprehendClient;

public interface ComprehendCaller<
    T extends AmazonWebServiceResult<ResponseMetadata>, R extends ComprehendRequestData> {

  String READ_ACTION_WITHOUT_FEATURES_EX =
      "If you chose TEXTRACT_ANALYZE_DOCUMENT as the read action, "
          + "you must specify one feature types";

  T call(ComprehendClient client, R requestData);
}
