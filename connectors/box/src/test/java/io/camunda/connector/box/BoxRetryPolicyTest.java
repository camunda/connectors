/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.box.sdkgen.networking.fetchoptions.FetchOptions;
import com.box.sdkgen.networking.fetchresponse.FetchResponse;
import io.camunda.connector.box.model.BoxRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class BoxRetryPolicyTest {

  private final FetchOptions options =
      new FetchOptions.Builder("https://api.box.com/2.0/files", "POST").build();
  private final FetchResponse unavailable = new FetchResponse(503, Map.of());

  @Test
  void neverRetriesUploads() {
    var upload = new BoxRequest.Operation.UploadFile("/", null, "file.txt");

    var strategy = BoxOperations.sessionFor(upload).getRetryStrategy();

    assertThat(strategy.shouldRetry(options, unavailable, 1)).isFalse();
    assertThat(strategy.shouldRetry(options, new FetchResponse(0, Map.of()), 1)).isFalse();
  }

  @Test
  void keepsDefaultRetriesForOtherOperations() {
    var delete = new BoxRequest.Operation.DeleteFile("/a.txt");

    var strategy = BoxOperations.sessionFor(delete).getRetryStrategy();

    assertThat(strategy.shouldRetry(options, unavailable, 1)).isTrue();
  }
}
