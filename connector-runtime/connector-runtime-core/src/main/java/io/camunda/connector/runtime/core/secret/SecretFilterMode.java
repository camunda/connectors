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

/**
 * Configures how a {@link SecretFilter} is built for a connector context.
 *
 * <p>{@code DISABLED} allows every secret name. {@code LAX} and {@code STRICT} both restrict
 * resolution to the secrets a connector's own configuration declares; they differ only where
 * building the allow-list can fail (for example the outbound filter's BPMN re-fetch), in which case
 * {@code LAX} falls back to allowing all and {@code STRICT} fails the connector instead. A source
 * that builds its allow-list synchronously from data it already holds, with no such failure mode,
 * may treat {@code LAX} and {@code STRICT} identically.
 */
public enum SecretFilterMode {
  DISABLED,
  LAX,
  STRICT
}
