/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.anthropic.core.http.Interceptor;
import java.util.function.Supplier;

/**
 * Injects a resolved bearer token per-request via {@link Interceptor#syncOnly}. Synchronous only:
 * this provider's chat model never calls the anthropic-java async client.
 */
public final class OAuthBearerTokenInterceptor {

  private OAuthBearerTokenInterceptor() {}

  public static Interceptor create(Supplier<String> bearerToken) {
    return Interceptor.syncOnly(
        (client, request, requestOptions) -> {
          final var authorizedRequest =
              request.toBuilder()
                  .replaceHeaders("Authorization", "Bearer " + bearerToken.get())
                  .build();
          return client.execute(authorizedRequest, requestOptions);
        });
  }
}
