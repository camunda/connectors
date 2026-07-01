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
package io.camunda.connector.optimizer.core;

import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.optimizer.pass.MergeByIdentityPass;
import io.camunda.connector.optimizer.pass.ReorderPass;
import io.camunda.connector.optimizer.pass.StrengthReducePass;
import io.camunda.connector.optimizer.pass.TotalizePass;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Library entrypoint for the template-optimizer pipeline.
 *
 * <p>The default pipeline is the same set of passes the standalone CLI runs and is what generators
 * invoke as a post-processing step before serializing templates to disk.
 */
public final class Optimizer {

  private static final List<Pass> DEFAULT_PASSES =
      List.of(
          new MergeByIdentityPass(),
          new TotalizePass(),
          new StrengthReducePass(),
          new ReorderPass());

  private final List<Pass> passes;

  private Optimizer(List<Pass> passes) {
    this.passes = List.copyOf(passes);
  }

  /** Returns an optimizer running every default pass in the canonical order. */
  public static Optimizer defaultPipeline() {
    return new Optimizer(DEFAULT_PASSES);
  }

  /**
   * Returns an optimizer running every default pass except those whose id is in {@code skip}.
   *
   * @throws IllegalArgumentException if any id in {@code skip} doesn't correspond to a known pass.
   *     We fail loud rather than silently no-op a misspelled flag, because the consequence of a
   *     silent no-op is that the user thinks they disabled a pass but the optimizer ran it anyway.
   */
  public static Optimizer defaultPipelineExcept(List<String> skip) {
    Set<String> knownIds = new LinkedHashSet<>();
    for (Pass pass : DEFAULT_PASSES) {
      knownIds.add(pass.id());
    }
    List<String> unknown = new ArrayList<>();
    for (String id : skip) {
      if (!knownIds.contains(id)) {
        unknown.add(id);
      }
    }
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "Unknown pass id(s) in skip list: " + unknown + ". Known passes: " + knownIds + ".");
    }
    List<Pass> filtered = new ArrayList<>();
    for (Pass pass : DEFAULT_PASSES) {
      if (!skip.contains(pass.id())) {
        filtered.add(pass);
      }
    }
    return new Optimizer(filtered);
  }

  /** Returns an optimizer running exactly the given passes. */
  public static Optimizer withPasses(List<Pass> passes) {
    return new Optimizer(passes);
  }

  /** Names and descriptions of the default-pipeline passes in order. */
  public static Map<String, Pass> defaultPasses() {
    Map<String, Pass> map = new LinkedHashMap<>();
    for (Pass pass : DEFAULT_PASSES) {
      map.put(pass.id(), pass);
    }
    return map;
  }

  /** Apply every pass in this optimizer's pipeline to the given template. */
  public ElementTemplate optimize(ElementTemplate template) {
    ElementTemplate current = template;
    for (Pass pass : passes) {
      current = pass.apply(current);
    }
    return current;
  }

  /** Returns the passes that will be applied, in order. */
  public List<Pass> passes() {
    return passes;
  }
}
