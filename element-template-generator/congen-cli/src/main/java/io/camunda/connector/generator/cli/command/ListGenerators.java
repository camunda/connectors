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
package io.camunda.connector.generator.cli.command;

import static io.camunda.connector.generator.cli.ReturnCodes.GENERATION_FAILED;
import static io.camunda.connector.generator.cli.ReturnCodes.SUCCESS;

import io.camunda.connector.generator.cli.GeneratorServiceLoader;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "list")
public class ListGenerators implements Callable<Integer> {

  public Integer call() {
    System.out.println("Available generators:");
    try {
      GeneratorServiceLoader.loadGenerators()
          .forEach(
              (key, generator) -> System.out.println(" - " + key + ", usage: " + generator.getUsage()));
    } catch (Exception e) {
      System.err.println("Failed to list generators: " + e.getMessage());
      return GENERATION_FAILED.getCode();
    }
    return SUCCESS.getCode();
  }
}
