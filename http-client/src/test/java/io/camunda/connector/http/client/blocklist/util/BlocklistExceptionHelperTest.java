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
package io.camunda.connector.http.client.blocklist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import org.junit.jupiter.api.Test;

public class BlocklistExceptionHelperTest {

  @Test
  public void testThrowBlocklistExceptionWithTypeAndName() {
    ConnectorInputException exception =
        assertThrows(
            ConnectorInputException.class,
            () -> BlocklistExceptionHelper.throwBlocklistException("URL", "BLOCK_NAME"));
    assertThat(exception.getMessage())
        .contains("The provided URL is not allowed (Block Name: BLOCK_NAME)");
  }

  @Test
  public void testThrowBlocklistExceptionWithTypeOnly() {
    ConnectorInputException exception =
        assertThrows(
            ConnectorInputException.class,
            () -> BlocklistExceptionHelper.throwBlocklistException("URL", ""));
    assertThat(exception.getMessage()).contains("The provided URL is not allowed");
  }

  @Test
  public void testExceptionMessageDoesNotContainBlockNameWhenEmpty() {
    ConnectorInputException exception =
        assertThrows(
            ConnectorInputException.class,
            () -> BlocklistExceptionHelper.throwBlocklistException("URL", ""));
    assertThat(exception.getMessage()).doesNotContain("Block Name");
  }
}
