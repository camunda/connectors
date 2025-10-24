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
package io.camunda.connector.http.client.utils;

public class EnvVarHelper {

  private static final String ENV_VAR_MAX_BODY_SIZE = "CONNECTOR_HTTP_CLIENT_MAX_BODY_SIZE";

  public static int getMaxInMemoryBodySize() {
    String envVar = System.getenv(ENV_VAR_MAX_BODY_SIZE);
    if (envVar != null) {
      try {
        int size = Integer.parseInt(envVar);
        if (size > 0) {
          return size;
        } else {
          throw new IllegalArgumentException(
              "Environment variable "
                  + ENV_VAR_MAX_BODY_SIZE
                  + " must be a positive integer, but was: "
                  + envVar);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Environment variable "
                + ENV_VAR_MAX_BODY_SIZE
                + " must be a valid integer, but was: "
                + envVar,
            e);
      }
    }
    return 50 * 1024 * 1024; // Default to 50 MB
  }
}
