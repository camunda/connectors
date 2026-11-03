/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aSendMessageResult;

/** Receives the response of a message sent with an {@link A2aSdkClient}. */
public interface A2aSdkClientListener {

  void onResult(A2aSendMessageResult result);

  void onError(Throwable error);

  /** Listener for clients that never send messages. */
  A2aSdkClientListener NOOP =
      new A2aSdkClientListener() {
        @Override
        public void onResult(A2aSendMessageResult result) {}

        @Override
        public void onError(Throwable error) {}
      };
}
