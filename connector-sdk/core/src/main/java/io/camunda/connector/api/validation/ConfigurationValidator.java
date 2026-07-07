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
 * Implemented by a configuration (credential) class to make it validatable out-of-band.
 *
 * <p>A configuration class is a data class annotated with {@code @ConfigurationTemplate} (e.g.
 * {@code AwsCredentialConfiguration}). By implementing this interface, it declares how to check
 * whether an instance of itself is usable — for a credential, typically whether it can
 * authenticate. Because the logic lives on the configuration class, it is shared by every connector
 * that consumes that configuration rather than duplicated per connector.
 *
 * <p>The runtime resolves a stored configuration instance, deserializes it into this class, and
 * calls {@link #validate()}. Implementations return {@link ConfigurationValidationResult#success()}
 * or {@link ConfigurationValidationResult#failure(String, String)}; a thrown exception is mapped to
 * a failure by the runtime.
 */
public interface ConfigurationValidator {

  ConfigurationValidationResult validate();
}
