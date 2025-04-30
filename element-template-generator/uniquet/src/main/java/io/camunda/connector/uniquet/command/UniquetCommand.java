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
package io.camunda.connector.uniquet.command;

import io.camunda.connector.uniquet.core.ConnectorsFinder;
import io.camunda.connector.uniquet.core.IndexWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;

public class UniquetCommand implements Callable<Integer> {

  @CommandLine.Option(
      names = {"-d", "--directory"},
      defaultValue = "connectors")
  private String gitDirectory;

  @CommandLine.Option(names = {"-o", "--output-file"})
  private String outputFile;

  @Override
  public Integer call() {
    try {
      IndexWriter.create(
              ConnectorsFinder.create(Path.of(gitDirectory)).getAllConnectors(),
              Path.of(outputFile))
          .persist();
    } catch (RuntimeException e) {
      e.printStackTrace();
      return 1;
    }
    return 0;
  }
}
