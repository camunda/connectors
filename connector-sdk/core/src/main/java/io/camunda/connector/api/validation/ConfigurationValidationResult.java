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
 * Outcome of validating a configuration (credential) via {@link
 * ConfigurationValidator#validate(Object)}.
 *
 * <ul>
 *   <li>{@code SUCCESS} — the configuration is usable.
 *   <li>{@code FAILURE} — the configuration is not usable; {@code code} and {@code message}
 *       describe why (e.g. {@code UNAUTHORIZED}).
 *   <li>{@code UNSUPPORTED} — no validator is registered for the requested configuration id.
 *       Produced by the runtime, not by connector authors.
 * </ul>
 *
 * <p>Connector authors return {@link #success()} or {@link #failure(ErrorCode, String)}, preferring
 * a shared {@link ErrorCode} over inventing a per-connector one.
 */
public record ConfigurationValidationResult(Status status, String code, String message) {

  public enum Status {
    SUCCESS,
    FAILURE,
    UNSUPPORTED
  }

  /**
   * The failure codes shared by every configuration validator, so that clients can branch on a
   * known set instead of on strings coined independently by each connector.
   *
   * <p>{@code code} stays a {@code String} on the record rather than this enum: a validator may
   * still surface a domain-specific code via {@link #failure(String, String)}, and a {@code
   * ConnectorException} thrown out of a validator carries an arbitrary error code that the runtime
   * passes through. Reach for those only when no constant below fits.
   */
  public enum ErrorCode {
    /** The target system rejected the credential. */
    UNAUTHORIZED,
    /** The configuration is structurally invalid — a required value is missing or malformed. */
    INVALID_INPUT,
    /** The stored configuration could not be resolved from its reference. */
    RESOLUTION_ERROR,
    /** Validation could not be completed, for any other reason. */
    ERROR
  }

  public static ConfigurationValidationResult success() {
    return new ConfigurationValidationResult(Status.SUCCESS, null, null);
  }

  public static ConfigurationValidationResult failure(ErrorCode code, String message) {
    return new ConfigurationValidationResult(Status.FAILURE, code.name(), message);
  }

  /**
   * Escape hatch for a code {@link ErrorCode} does not cover. Prefer {@link #failure(ErrorCode,
   * String)} so clients keep a single set of codes to branch on.
   */
  public static ConfigurationValidationResult failure(String code, String message) {
    return new ConfigurationValidationResult(Status.FAILURE, code, message);
  }

  public static ConfigurationValidationResult unsupported() {
    return new ConfigurationValidationResult(Status.UNSUPPORTED, null, null);
  }
}
