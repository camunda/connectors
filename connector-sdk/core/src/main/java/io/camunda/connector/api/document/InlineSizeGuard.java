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
package io.camunda.connector.api.document;

import io.camunda.connector.api.error.ConnectorInputException;

public final class InlineSizeGuard {

  // Zeebe safe limit for commands that include variables (e.g. complete-job):
  // https://docs.camunda.io/docs/components/concepts/variables/#variable-size-limitation
  public static final long MAX_INLINE_BYTES = 3L * 1024 * 1024 / 2; // 1.5 MB

  private InlineSizeGuard() {}

  public static void check(long sizeBytes) {
    if (sizeBytes > MAX_INLINE_BYTES) {
      throw new ConnectorInputException(
          "Output variables payload (%d bytes) exceeds the 1.5 MB safe variable size limit for Zeebe job completion. To handle large payloads, enable the 'Create document' option where available."
              .formatted(sizeBytes));
    }
  }
}
