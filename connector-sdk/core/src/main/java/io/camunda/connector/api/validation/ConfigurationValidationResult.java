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
package io.camunda.connector.api.validation;

/**
 * Outcome of validating a configuration (credential) via {@link ConfigurationValidator#validate()}.
 *
 * <ul>
 *   <li>{@code SUCCESS} — the configuration is usable.
 *   <li>{@code FAILURE} — the configuration is not usable; {@code code} and {@code message}
 *       describe why (e.g. {@code UNAUTHORIZED}).
 *   <li>{@code UNSUPPORTED} — no validator is registered for the requested configuration id.
 *       Produced by the runtime, not by connector authors.
 * </ul>
 *
 * <p>Connector authors return {@link #success()} or {@link #failure(String, String)}.
 */
public record ConfigurationValidationResult(Status status, String code, String message) {

  public enum Status {
    SUCCESS,
    FAILURE,
    UNSUPPORTED
  }

  public static ConfigurationValidationResult success() {
    return new ConfigurationValidationResult(Status.SUCCESS, null, null);
  }

  public static ConfigurationValidationResult failure(String code, String message) {
    return new ConfigurationValidationResult(Status.FAILURE, code, message);
  }

  public static ConfigurationValidationResult unsupported() {
    return new ConfigurationValidationResult(Status.UNSUPPORTED, null, null);
  }
}
