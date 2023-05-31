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
package io.camunda.connector.runtime.core.discovery;

import io.camunda.connector.api.secret.SecretProvider;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/** Static utility class for discovering {@link SecretProvider} implementations via SPI. */
public class SPISecretProviderDiscovery {

  /**
   * Discovers the {@link SecretProvider} implementations via SPI.
   *
   * @return the discovered {@link SecretProvider} implementations
   */
  public static List<SecretProvider> discover() {
    return ServiceLoader.load(SecretProvider.class).stream()
        .map(ServiceLoader.Provider::get)
        .collect(Collectors.toList());
  }
}
