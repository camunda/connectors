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
package io.camunda.connector.optimizer.command;

import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.optimizer.core.Optimizer;
import io.camunda.connector.optimizer.core.Pass;
import io.camunda.connector.optimizer.core.TemplateLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "element-template-optimizer",
    description = "Optimize element templates by compacting conditional properties",
    mixinStandardHelpOptions = true,
    subcommands = {ListPassesCommand.class})
public class OptimizerCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Template file to optimize", paramLabel = "INPUT_FILE")
  private Path inputFile;

  @Option(
      names = {"-o", "--output"},
      description = "Output file path (default: rewrite the input file in place)")
  private Path outputFile;

  @Option(
      names = {"--dry-run"},
      description = "Print whether changes would be made without writing anything")
  private boolean dryRun;

  @Option(
      names = {"--skip-passes"},
      split = ",",
      description = "Comma-separated pass IDs to skip (e.g. merge-by-identity,totalize)")
  private List<String> skipPasses = new ArrayList<>();

  @Override
  public Integer call() throws Exception {
    if (!Files.isRegularFile(inputFile)) {
      System.err.println("Error: input file does not exist or is not a regular file: " + inputFile);
      return 1;
    }

    Optimizer optimizer;
    try {
      optimizer = Optimizer.defaultPipelineExcept(skipPasses);
    } catch (IllegalArgumentException e) {
      System.err.println("Error: " + e.getMessage());
      return 2;
    }
    if (optimizer.passes().isEmpty()) {
      System.err.println("Error: every pass was skipped, nothing to do");
      return 1;
    }

    System.out.println("Optimizing: " + inputFile);
    System.out.println("Passes: " + optimizer.passes().stream().map(Pass::id).toList());

    ElementTemplate original;
    try {
      original = TemplateLoader.load(inputFile);
    } catch (java.io.IOException e) {
      System.err.println("Error: failed to read template " + inputFile + ": " + e.getMessage());
      return 3;
    }
    ElementTemplate optimized;
    try {
      optimized = optimizer.optimize(original);
    } catch (RuntimeException e) {
      System.err.println("Error: optimizer pass failed on " + inputFile + ": " + e.getMessage());
      return 4;
    }

    String originalJson = TemplateLoader.toString(original);
    String optimizedJson = TemplateLoader.toString(optimized);
    boolean changed = !originalJson.equals(optimizedJson);

    if (dryRun) {
      System.out.println(changed ? "Would rewrite: " + inputFile : "No changes needed");
      return 0;
    }

    Path output = outputFile != null ? outputFile : inputFile;
    if (outputFile != null && outputFile.getParent() != null) {
      Files.createDirectories(outputFile.getParent());
    }
    TemplateLoader.save(optimized, output);
    System.out.println(changed ? "Wrote: " + output : "No changes needed; wrote: " + output);
    return 0;
  }
}

@Command(
    name = "list-passes",
    description = "List available optimization passes",
    mixinStandardHelpOptions = true)
class ListPassesCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    System.out.println("Available optimization passes:");
    System.out.println();
    for (Map.Entry<String, Pass> entry : Optimizer.defaultPasses().entrySet()) {
      System.out.println("  " + entry.getKey());
      System.out.println("    " + entry.getValue().description());
      System.out.println();
    }
    return 0;
  }
}
