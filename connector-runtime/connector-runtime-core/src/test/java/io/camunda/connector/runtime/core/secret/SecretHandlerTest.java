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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecretHandlerTest {

  @Mock private SecretProvider secretProvider;

  @Test
  void replaceSecrets_allowedKey_resolvesFromProvider() {
    when(secretProvider.getSecret("ALLOWED")).thenReturn("VALUE");
    var handler =
        new SecretHandler(
            secretProvider, SecretFilter.allowOnly(List.of(new Secret("ALLOWED", List.of()))));

    var result = handler.replaceSecrets("{{secrets.ALLOWED}}");

    assertThat(result).isEqualTo("VALUE");
  }

  @Test
  void replaceSecrets_notAllowedKey_leavesPlaceholderUnreplacedAndNeverCallsProvider() {
    var handler =
        new SecretHandler(
            secretProvider, SecretFilter.allowOnly(List.of(new Secret("ALLOWED", List.of()))));

    var result = handler.replaceSecrets("{{secrets.NOT_ALLOWED}}");

    assertThat(result).isEqualTo("{{secrets.NOT_ALLOWED}}");
    verify(secretProvider, never()).getSecret("NOT_ALLOWED");
  }
}
