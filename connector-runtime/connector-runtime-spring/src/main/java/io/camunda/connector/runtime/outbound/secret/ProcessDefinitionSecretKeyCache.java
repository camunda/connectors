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
package io.camunda.connector.runtime.outbound.secret;

import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import io.camunda.zeebe.model.bpmn.instance.SendTask;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.FeelEngineBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

public class ProcessDefinitionSecretKeyCache implements SecretKeyCache {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionSecretKeyCache.class);
  private static final FeelEngineApi FEEL_ENGINE = FeelEngineBuilder.forJava().build();
  private static final List<Class<? extends BaseElement>> OUTBOUND_ELIGIBLE_TYPES =
      new ArrayList<>();

  static {
    OUTBOUND_ELIGIBLE_TYPES.add(ServiceTask.class);
    OUTBOUND_ELIGIBLE_TYPES.add(SendTask.class);
    OUTBOUND_ELIGIBLE_TYPES.add(ScriptTask.class);
    OUTBOUND_ELIGIBLE_TYPES.add(BusinessRuleTask.class);
    OUTBOUND_ELIGIBLE_TYPES.add(SubProcess.class);
    OUTBOUND_ELIGIBLE_TYPES.add(IntermediateThrowEvent.class);
    OUTBOUND_ELIGIBLE_TYPES.add(EndEvent.class);
  }

  private final String physicalTenantId;
  private final CamundaClient camundaClient;
  private final Cache cache;

  /**
   * Source/binary-compatibility overload for existing callers compiled against the original
   * single-tenant {@code (CamundaClient, Cache)} constructor: defaults {@code physicalTenantId} to
   * {@code "default"}, matching this class's original (pre-#6961) single-tenant behavior.
   */
  public ProcessDefinitionSecretKeyCache(CamundaClient camundaClient, Cache cache) {
    this("default", camundaClient, cache);
  }

  /**
   * @param physicalTenantId identifies the physical tenant this instance's {@code camundaClient}
   *     belongs to. Mixed into the (shared, bounded) cache key so that two physical tenants whose
   *     {@code processDefinitionKey} values happen to collide don't return each other's secret keys
   *     — mirrors the equivalent fix for {@code ProcessDefinitionInspector} on the inbound side.
   */
  public ProcessDefinitionSecretKeyCache(
      String physicalTenantId, CamundaClient camundaClient, Cache cache) {
    this.physicalTenantId = physicalTenantId;
    this.camundaClient = camundaClient;
    this.cache = cache;
  }

  private record CachedProcessDefinitionKey(String physicalTenantId, long processDefinitionKey) {}

  @Override
  public List<Secret> getSecretKeys(SecretKeyContext secretKeyContext) {
    var cacheKey =
        new CachedProcessDefinitionKey(physicalTenantId, secretKeyContext.processDefinitionKey());
    return cache
        .get(cacheKey, () -> fetchSecretKeysByElementIds(secretKeyContext.processDefinitionKey()))
        .getOrDefault(secretKeyContext.elementId(), Collections.emptyList());
  }

  private Map<String, List<Secret>> fetchSecretKeysByElementIds(long processDefinitionKey) {
    String bpmnXml =
        camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).execute();

    BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(new ByteArrayInputStream(bpmnXml.getBytes()));
    var processes =
        modelInstance.getDefinitions().getChildElementsByType(Process.class).stream().toList();

    return processes.stream()
        .flatMap(process -> inspectBpmnProcess(process, processDefinitionKey).entrySet().stream())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Map<String, List<Secret>> inspectBpmnProcess(Process process, long processDefinitionKey) {
    Collection<BaseElement> outboundEligibleElements =
        retrieveOutboundEligibleElementsFromProcess(process);
    if (outboundEligibleElements.isEmpty()) {
      LOG.debug(
          "No connector elements found in process definition with key {}", processDefinitionKey);
      return Collections.emptyMap();
    }

    Map<String, List<Secret>> discoveredOutboundConnectors = new HashMap<>();
    for (BaseElement element : outboundEligibleElements) {
      var inputs = findElementInput(element);
      var definedSecrets = extractSecrets(inputs);
      discoveredOutboundConnectors.put(element.getId(), definedSecrets);
    }
    return discoveredOutboundConnectors;
  }

  /**
   * A secret is allowed not only on the input that names it directly, but also on every input whose
   * own FEEL expression reads the variable that input's target writes — transitively. A template
   * commonly assembles one input's value (e.g. {@code url}) from others (e.g. {@code baseUrl},
   * itself holding a secret); the assembled input's runtime value can carry the secret's text just
   * as directly as the input that named it, so the allow-list has to follow that data flow rather
   * than stop at the input where the secret's name is written.
   *
   * <p>A dependency is resolved by matching a referenced variable's full qualified path against
   * another input's {@code target} path, one a prefix of the other (a dotted target like {@code
   * authentication.type} publishes both the top-level variable {@code authentication} and the field
   * {@code authentication.type}). Requiring a prefix relation on the full path — rather than just
   * the top-level segment — keeps sibling fields under the same top-level name, like {@code
   * authentication.type} and {@code authentication.token}, from being treated as dependent on each
   * other merely for sharing a top-level name.
   *
   * <p>Only a preceding, still-effective writer of the referenced path counts as a dependency:
   * {@code zeebe:input} mappings evaluate in declaration order, each against the output the ones
   * before it already built, so a later mapping's target is invisible to an earlier one, and a
   * target written more than once is reachable only through its last writer before this point.
   */
  private List<Secret> extractSecrets(List<ZeebeInput> inputs) {
    Map<ZeebeInput, List<String>> pathByInput = new LinkedHashMap<>();
    Map<ZeebeInput, List<String>> ownSecretNamesByInput = new LinkedHashMap<>();
    for (ZeebeInput input : inputs) {
      pathByInput.put(input, Arrays.asList(input.getTarget().split("\\.")));
      // Legacy-only: this allow-list gates SecretHandler's substitution of the legacy
      // {{secrets.X}}/secrets.X forms alone (SecretUtil.replaceSecrets never touches the new
      // camunda.secrets.<name> form -- the engine substitutes that one before the job is
      // activated, per ADR-0007). Including the new form's names here (as
      // SecretUtil.retrieveSecretKeysInInput does) would let a `camunda.secrets.X` declaration
      // also authorize a legacy `secrets.X` lookup at that path, crossing the two secret stores --
      // mirrors InboundConnectorContextImpl's equivalent allow-list, which already excludes it.
      ownSecretNamesByInput.put(
          input,
          SecretUtil.retrieveLegacySecretKeysInInput(input.getSource()).stream()
              .map(String::trim)
              .distinct()
              .toList());
    }

    // zeebe:input mappings are evaluated in declaration order, each against the output built by
    // the ones before it -- a later mapping is invisible to an earlier one, and a target written
    // more than once is only reachable through its last (nearest-preceding), still-effective
    // writer. effectiveTargets is therefore built up one input at a time, alongside the loop, and
    // an input's own dependencies are resolved against it before that input registers itself.
    Map<ZeebeInput, List<ZeebeInput>> directDependencies = new LinkedHashMap<>();
    Map<List<String>, ZeebeInput> effectiveTargets = new LinkedHashMap<>();
    for (ZeebeInput input : inputs) {
      List<List<String>> referencedPaths = referencedVariablePaths(input.getSource());
      directDependencies.put(
          input,
          referencedPaths.stream()
              .flatMap(referencedPath -> matchingWriters(referencedPath, effectiveTargets))
              .distinct()
              .toList());
      // A write to a parent path replaces the whole subtree beneath it, so every existing writer
      // of a descendant path is no longer effective from this point on.
      List<String> ownPath = pathByInput.get(input);
      effectiveTargets.keySet().removeIf(existingPath -> isProperPrefix(ownPath, existingPath));
      effectiveTargets.put(ownPath, input);
    }

    List<Secret> result = new ArrayList<>();
    for (ZeebeInput input : inputs) {
      List<String> path = pathByInput.get(input);
      // A later mapping can overwrite this input's own target -- directly, or by writing to a
      // parent path that replaces the whole subtree beneath it -- before the sequence finishes.
      // effectiveTargets reflects the final state once the loop above has processed every input,
      // so if it no longer maps `path` back to this exact input, this input's value is not
      // reachable at `path` any more; granting the secret there would let whatever the actual
      // final writer put in that field -- potentially attacker-controlled -- resolve a secret it
      // never carried.
      if (effectiveTargets.get(path) == input) {
        ownSecretNamesByInput.get(input).forEach(name -> result.add(new Secret(name, path)));
      }

      Set<ZeebeInput> visited = new HashSet<>();
      Deque<ZeebeInput> pending = new ArrayDeque<>(directDependencies.get(input));
      while (!pending.isEmpty()) {
        ZeebeInput dependency = pending.poll();
        if (!visited.add(dependency)) {
          continue;
        }
        ownSecretNamesByInput.get(dependency).forEach(name -> result.add(new Secret(name, path)));
        pending.addAll(directDependencies.getOrDefault(dependency, List.of()));
      }
    }
    return result.stream().distinct().toList();
  }

  /**
   * The full qualified path of every variable a FEEL expression references, or an empty list if
   * {@code source} isn't a FEEL expression (no leading {@code =}) or fails to parse — a static
   * value or a malformed expression simply has nothing to propagate from.
   */
  private static List<List<String>> referencedVariablePaths(String source) {
    if (source == null) {
      return List.of();
    }
    String trimmed = source.trim();
    if (!trimmed.startsWith("=")) {
      return List.of();
    }
    var parseResult = FEEL_ENGINE.parseExpression(trimmed.substring(1));
    if (parseResult.isFailure()) {
      return List.of();
    }
    return parseResult.parsedExpression().getVariableReferences().stream()
        .map(ref -> ref.getFullQualifiedName())
        .distinct()
        .toList();
  }

  /**
   * The writers a reference to {@code referencedPath} depends on, given the writers currently still
   * effective. Two disjoint cases, since a single symmetric prefix match would let a stale ancestor
   * writer stand in for a leaf that a later, more specific writer has since replaced:
   *
   * <ul>
   *   <li>The reference targets a specific field or the exact field a writer wrote: at most one
   *       writer answers it, the <em>nearest</em> (most specific) ancestor-or-self path among the
   *       effective writers -- a closer writer, even an earlier one, shadows a more distant
   *       ancestor's stake in that one field.
   *   <li>The reference targets a whole object a writer's field lives under: every writer whose
   *       path is a descendant of the reference answers it, since reading the whole object reads
   *       every field currently set beneath it.
   * </ul>
   */
  private static Stream<ZeebeInput> matchingWriters(
      List<String> referencedPath, Map<List<String>, ZeebeInput> effectiveTargets) {
    var nearestAncestorOrSelf =
        effectiveTargets.entrySet().stream()
            .filter(entry -> isAncestorOrSelf(entry.getKey(), referencedPath))
            .max(Comparator.comparingInt(entry -> entry.getKey().size()))
            .map(Map.Entry::getValue);
    var descendants =
        effectiveTargets.entrySet().stream()
            .filter(entry -> isProperPrefix(referencedPath, entry.getKey()))
            .map(Map.Entry::getValue);
    return nearestAncestorOrSelf
        .map(writer -> Stream.concat(Stream.of(writer), descendants))
        .orElse(descendants);
  }

  /** Whether {@code ancestor} is a prefix of {@code path}, including being equal to it. */
  private static boolean isAncestorOrSelf(List<String> ancestor, List<String> path) {
    return ancestor.size() <= path.size() && path.subList(0, ancestor.size()).equals(ancestor);
  }

  /** Whether {@code shorter} is a strict ancestor path of {@code longer} (not equal to it). */
  private static boolean isProperPrefix(List<String> shorter, List<String> longer) {
    return shorter.size() < longer.size() && longer.subList(0, shorter.size()).equals(shorter);
  }

  private List<ZeebeInput> findElementInput(BaseElement element) {
    ZeebeIoMapping singleExtensionElement = element.getSingleExtensionElement(ZeebeIoMapping.class);
    if (singleExtensionElement == null) {
      return Collections.emptyList();
    }
    return singleExtensionElement.getInputs().stream().toList();
  }

  private Collection<BaseElement> retrieveOutboundEligibleElementsFromProcess(
      final Process process) {
    // process is root element in graph
    Collection<FlowElement> buffer = new HashSet<>();
    Collection<FlowElement> allElements = collectFlowElements(process.getFlowElements(), buffer);
    Collection<BaseElement> outboundEligibleElements = new HashSet<>();
    for (FlowElement element : allElements) {
      OUTBOUND_ELIGIBLE_TYPES.forEach(
          iet -> {
            if (iet.isInstance(element)) {
              outboundEligibleElements.add(element);
            }
          });
    }
    return outboundEligibleElements;
  }

  // pre-existing, move to util from ProcessDefinitionInspector
  private Collection<FlowElement> collectFlowElements(
      final Collection<FlowElement> processFlowElements, final Collection<FlowElement> buffer) {
    for (FlowElement element : processFlowElements) {
      // a subprocess (embedded, event, multi-instance, ad-hoc, or nested) can itself be a
      // connector element (its own zeebe:ioMapping declares secrets, e.g. the AI Agent Sub-process
      // template on an ad-hoc subprocess), so it must be considered directly, in addition to
      // expanding its children below
      if (element instanceof SubProcess subprocess) {
        buffer.add(subprocess);
        buffer.addAll(retrieveEligibleElementsFromSubprocess(subprocess));
        continue;
      }
      buffer.add(element);
    }
    return buffer;
  }

  // pre-existing, move to util from ProcessDefinitionInspector
  private Collection<FlowElement> retrieveEligibleElementsFromSubprocess(
      final SubProcess subprocess) {
    // Subprocesses can contain other subprocesses
    Collection<FlowElement> buffer = new HashSet<>();
    Collection<FlowElement> processFlowElements = subprocess.getFlowElements();
    return collectFlowElements(processFlowElements, buffer);
  }
}
