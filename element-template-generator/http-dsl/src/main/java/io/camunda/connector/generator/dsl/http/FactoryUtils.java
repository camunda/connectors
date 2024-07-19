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
package io.camunda.connector.generator.dsl.http;

import io.camunda.connector.generator.api.CliCompatibleTemplateGenerator;
import java.util.List;

public class FactoryUtils {

  public static List<CliCompatibleTemplateGenerator.Operation> transformOperationParseResults(
      List<OperationParseResult> operations) {
    return operations.stream()
        .map(
            o ->
                new CliCompatibleTemplateGenerator.Operation.Builder()
                    .id(o.id())
                    .path(o.path())
                    .method(o.method().name())
                    .tags(o.tags())
                    .supported(o.supported())
                    .build())
        .toList();
  }
}
