/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import com.box.sdkgen.networking.fetchoptions.FetchOptions;
import com.box.sdkgen.networking.fetchresponse.FetchResponse;
import com.box.sdkgen.networking.retries.BoxRetryStrategy;
import com.box.sdkgen.networking.retries.RetryStrategy;
import io.camunda.connector.api.error.ConnectorRetryException;
import java.io.IOException;
import java.io.InputStream;

public class BodyAwareRetryStrategy implements RetryStrategy {

  private final RetryStrategy delegate = new BoxRetryStrategy();

  @Override
  public boolean shouldRetry(FetchOptions options, FetchResponse response, int attemptNumber) {
    if (options.getFileStream() != null || options.getMultipartData() != null) {
      return false;
    }
    boolean retry;
    try {
      retry = delegate.shouldRetry(options, response, attemptNumber);
    } catch (RuntimeException e) {
      closeQuietly(response.getContent());
      throw e;
    }
    if (retry || isFollowedRedirect(options, response)) {
      closeQuietly(response.getContent());
    } else if (response.getStatus() == 202 && response.getContent() != null) {
      closeQuietly(response.getContent());
      throw ConnectorRetryException.builder()
          .errorCode("FILE_NOT_READY")
          .message("Box has not finished preparing the file for download yet. Retry later.")
          .build();
    }
    return retry;
  }

  @Override
  public double retryAfter(FetchOptions options, FetchResponse response, int attemptNumber) {
    return delegate.retryAfter(options, response, attemptNumber);
  }

  private static boolean isFollowedRedirect(FetchOptions options, FetchResponse response) {
    return response.getStatus() >= 300
        && response.getStatus() < 400
        && Boolean.TRUE.equals(options.getFollowRedirects());
  }

  private static void closeQuietly(InputStream content) {
    if (content == null) {
      return;
    }
    try {
      content.close();
    } catch (IOException ignored) {
      // the response is discarded anyway
    }
  }
}
