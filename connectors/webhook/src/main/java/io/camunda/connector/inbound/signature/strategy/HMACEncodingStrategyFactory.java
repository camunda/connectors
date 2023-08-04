/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature.strategy;

import io.camunda.connector.inbound.model.HMACScope;
import io.camunda.connector.inbound.utils.HttpMethods;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

public class HMACEncodingStrategyFactory {

  private static final Map<BiPredicate<HMACScope[], String>, HMACEncodingStrategy> STRATEGY_MAP =
      new HashMap<>();

  static {
    STRATEGY_MAP.put(
        (scopes, method) ->
            checkAllMatch(scopes, HMACScope.URL, HMACScope.PARAMETERS, HMACScope.BODY)
                && isGetMethod(method),
        new URLAndParametersEncodingStrategy());
    STRATEGY_MAP.put(
        (scopes, method) ->
            checkAllMatch(scopes, HMACScope.URL, HMACScope.PARAMETERS, HMACScope.BODY)
                && !isGetMethod(method),
        new URLAndBodyEncodingStrategy());
    STRATEGY_MAP.put(
        (scopes, method) -> checkAllMatch(scopes, HMACScope.URL, HMACScope.BODY),
        new URLAndBodyEncodingStrategy());
    STRATEGY_MAP.put(
        (scopes, method) -> checkAllMatch(scopes, HMACScope.URL, HMACScope.PARAMETERS),
        new URLAndParametersEncodingStrategy());
    STRATEGY_MAP.put(
        (scopes, method) -> checkAllMatch(scopes, HMACScope.BODY), new BodyEncodingStrategy());
  }

  public static HMACEncodingStrategy getStrategy(
      final HMACScope[] hmacScopes, final String method) {
    return STRATEGY_MAP.entrySet().stream()
        .filter(entry -> entry.getKey().test(hmacScopes, method))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseThrow(
            () ->
                new UnsupportedOperationException(
                    "The current pattern of HMAC scopes "
                        + Arrays.toString(hmacScopes)
                        + " and method "
                        + method
                        + " is not supported"));
  }

  private static boolean checkAllMatch(final HMACScope[] hmacScopes, final HMACScope... scopes) {
    return hmacScopes.length == scopes.length
        && Arrays.stream(hmacScopes).allMatch(s -> Arrays.asList(scopes).contains(s));
  }

  private static boolean isGetMethod(final String method) {
    return HttpMethods.get.name().equalsIgnoreCase(method);
  }
}
