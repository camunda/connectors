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
package io.camunda.connector.runtime.managedcode;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

final class LocalProcessEnvironment {

  private static final Set<String> ALLOWED_HOST_VARIABLES =
      Set.of("LANG", "LC_ALL", "LC_CTYPE", "TZ", "SYSTEMROOT", "WINDIR");

  private LocalProcessEnvironment() {}

  static void configure(Map<String, String> environment, Path temporaryDirectory) {
    final var hostEnvironment = System.getenv();
    environment.clear();
    for (var name : ALLOWED_HOST_VARIABLES) {
      final var value = hostEnvironment.get(name);
      if (value != null) {
        environment.put(name, value);
      }
    }
    final var temporaryPath = temporaryDirectory.toAbsolutePath().normalize().toString();
    environment.put("HOME", temporaryPath);
    environment.put("TMPDIR", temporaryPath);
    environment.put("TMP", temporaryPath);
    environment.put("TEMP", temporaryPath);
    environment.put("PYTHONDONTWRITEBYTECODE", "1");
    environment.put("NODE_DISABLE_COLORS", "1");
  }
}
