/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import io.camunda.connector.comprehend.model.ComprehendRequestData;

/**
 * @param <C> the AWS SDK v2 client type this caller invokes -- {@code SyncComprehendCaller} and
 *     {@code AsyncComprehendCaller} use unrelated client interfaces (unlike AWS SDK v1, where the
 *     async client was a subtype of the sync one), so the client type is parameterized here rather
 *     than fixed to a single supertype.
 * @param <R> the connector request data this caller consumes
 * @param <T> the connector-owned result this caller returns
 */
public interface ComprehendCaller<C, R extends ComprehendRequestData, T> {

  String READ_ACTION_WITHOUT_FEATURES_EX =
      "If you chose TEXTRACT_ANALYZE_DOCUMENT as the read action, "
          + "you must specify one feature types";

  T call(C client, R requestData);
}
