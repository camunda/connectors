/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.http.SdkHttpResponse;

/**
 * HTTP-response metadata shared by every AWS Comprehend v2 response, reconstructed from {@code
 * response.sdkHttpResponse()}.
 *
 * <p>Mirrors the {@code sdkHttpMetadata} object the pre-v2 (AWS SDK v1) connector exposed, and is
 * shared by both {@link ComprehendClassifyResult} and {@link ComprehendClassificationJobResult}
 * since both response types carry the same kind of HTTP metadata.
 */
@JsonPropertyOrder({"httpHeaders", "httpStatusCode", "allHttpHeaders"})
public record SdkHttpMetadata(
    Map<String, String> httpHeaders,
    Integer httpStatusCode,
    Map<String, List<String>> allHttpHeaders) {

  public static SdkHttpMetadata from(final SdkHttpResponse httpResponse) {
    if (httpResponse == null) {
      return null;
    }
    Map<String, List<String>> allHttpHeaders = new LinkedHashMap<>(httpResponse.headers());
    Map<String, String> httpHeaders = new LinkedHashMap<>();
    allHttpHeaders.forEach(
        (name, values) ->
            httpHeaders.put(name, values == null || values.isEmpty() ? null : values.get(0)));
    return new SdkHttpMetadata(httpHeaders, httpResponse.statusCode(), allHttpHeaders);
  }
}
