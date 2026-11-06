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
package io.camunda.connector.managedscript.api;

import java.util.Objects;

/**
 * An executor that the runtime may call, as {@link ManagedCodeProvider#executors()} reports it.
 *
 * @param name the name of the function on the platform, such as {@code camunda-ms-py-0123456789ab}
 * @param logGroup the log group that belongs to the function alone and is deleted with it, such as
 *     {@code /aws/lambda/<name>}; {@code null} when the platform has none
 */
public record ExecutorReference(ScriptLanguage language, String name, String logGroup) {

  public ExecutorReference {
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(name, "name");
  }
}
