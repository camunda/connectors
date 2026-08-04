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
 * Validates a configuration (credential) of type {@code T} out-of-band.
 *
 * <p>{@code T} is a data class annotated with {@code @Configuration} (e.g. {@code
 * AwsCredentialConfiguration}); its {@code @Configuration#id()} is how a validator is matched to a
 * stored configuration. A single implementation validates one configuration type and is therefore
 * shared by every connector that consumes it — the validation is written once, for the
 * configuration, not per connector.
 *
 * <p>Keeping the validator separate from the configuration class lets the configuration class stay
 * a pure modeling artifact (loadable by the element-template generator with no runtime SDKs), while
 * the validator carries the heavy runtime dependencies (an AWS/HTTP/JDBC client) that only the
 * runtime needs.
 *
 * <p>The runtime resolves a stored configuration instance, deserializes it into {@code T}, and
 * calls {@link #validate(Object)}. Implementations return {@link
 * ConfigurationValidationResult#success()} or {@link ConfigurationValidationResult#failure(String,
 * String)}; a thrown exception is mapped to a failure by the runtime.
 *
 * @param <T> the configuration type this validator handles
 */
public interface ConfigurationValidator<T> {

  ConfigurationValidationResult validate(T configuration);
}
