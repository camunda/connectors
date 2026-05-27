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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

  /** Exit codes returned by the optimizer CLI. */
  enum ExitCode {
    SUCCESS(0),
    /** Input file is missing, unreadable, or not a regular file. */
    BAD_INPUT(1),
    /** CLI arguments are invalid (e.g. unknown id in --skip-passes). */
    BAD_ARGS(2),
    /** Template JSON could not be parsed. */
    LOAD_FAILED(3),
    /** An optimizer pass threw an exception. */
    OPTIMIZE_FAILED(4);

    final int code;

    ExitCode(int code) {
      this.code = code;
    }
  }

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
      return ExitCode.BAD_INPUT.code;
    }

    Optimizer optimizer;
    try {
      optimizer = Optimizer.defaultPipelineExcept(skipPasses);
    } catch (IllegalArgumentException e) {
      System.err.println("Error: " + e.getMessage());
      return ExitCode.BAD_ARGS.code;
    }
    if (optimizer.passes().isEmpty()) {
      System.err.println("Error: every pass was skipped, nothing to do");
      return ExitCode.BAD_ARGS.code;
    }

    System.out.println("Optimizing: " + inputFile);
    System.out.println("Passes: " + optimizer.passes().stream().map(Pass::id).toList());

    ElementTemplate original;
    try {
      original = TemplateLoader.load(inputFile);
    } catch (IOException e) {
      System.err.println("Error: failed to read template " + inputFile + ": " + e.getMessage());
      return ExitCode.LOAD_FAILED.code;
    }
    ElementTemplate optimized;
    try {
      optimized = optimizer.optimize(original);
    } catch (RuntimeException e) {
      System.err.println("Error: optimizer pass failed on " + inputFile + ": " + e.getMessage());
      return ExitCode.OPTIMIZE_FAILED.code;
    }

    String originalJson = TemplateLoader.toString(original);
    String optimizedJson = TemplateLoader.toString(optimized);
    boolean changed = !originalJson.equals(optimizedJson);

    if (dryRun) {
      System.out.println(changed ? "Would rewrite: " + inputFile : "No changes needed");
      return ExitCode.SUCCESS.code;
    }

    Path output = outputFile != null ? outputFile : inputFile;
    if (outputFile != null && outputFile.getParent() != null) {
      Files.createDirectories(outputFile.getParent());
    }
    atomicWrite(optimized, output);
    System.out.println(changed ? "Wrote: " + output : "No changes needed; wrote: " + output);
    return ExitCode.SUCCESS.code;
  }

  /**
   * Serialize the template to a sibling temp file, then atomically move it onto the destination.
   * Prevents a Ctrl-C / crash between truncate and write from leaving the destination empty or
   * half-written — important for the default in-place rewrite mode.
   */
  private static void atomicWrite(ElementTemplate template, Path destination) throws IOException {
    Path dir = destination.toAbsolutePath().getParent();
    Path tempFile = Files.createTempFile(dir, ".optimizer-", ".tmp");
    try {
      TemplateLoader.save(template, tempFile);
      Files.move(
          tempFile,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (Throwable t) {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException ignored) {
        // Best effort; the temp file name is .optimizer-*.tmp so it's identifiable.
      }
      throw t;
    }
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
