/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.core.secret;

import java.util.List;
import java.util.Set;

/**
 * Determines whether a named secret may be resolved within a connector's context.
 *
 * <p>Use {@link #allowAll()} when no restriction applies and {@link #allowOnly(List)} to restrict
 * resolution to a declared set of secrets. An empty list passed to {@code allowOnly} denies all
 * secrets.
 */
@FunctionalInterface
public interface SecretFilter {

  /** Returns a filter that permits every secret name. */
  static SecretFilter allowAll() {
    return context -> true;
  }

  /**
   * Returns a filter that permits only the secret names in {@code names}. An empty list denies all
   * secrets.
   *
   * @param secrets the permitted secret contexts
   * @return a filter restricted to the given secrets
   */
  static SecretFilter allowOnly(List<Secret> secrets) {
    var allowed = Set.copyOf(secrets);
    return context ->
        allowed.stream()
            .filter(secret -> secret.secretName().equals(context.secretName()))
            .filter(secret -> context.fieldPath().size() >= secret.fieldPath().size())
            .anyMatch(
                secret ->
                    context
                        .fieldPath()
                        .subList(0, secret.fieldPath().size())
                        .equals(secret.fieldPath()));
  }

  /**
   * Returns {@code true} if the secret with the given name may be resolved.
   *
   * @param context the secret filter context
   * @return {@code true} to allow resolution, {@code false} to leave the reference as-is
   */
  boolean isAllowed(Secret context);

  record Secret(String secretName, List<String> fieldPath) {}
}
