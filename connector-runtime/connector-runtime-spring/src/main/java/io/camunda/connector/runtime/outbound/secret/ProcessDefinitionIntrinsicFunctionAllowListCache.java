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

import io.camunda.connector.document.jackson.IntrinsicFunctionModel;
import io.camunda.connector.runtime.core.intrinsic.AllowedIntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListContext;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.cache.Cache;

/**
 * Statically finds every {@code camunda.function.type} call a process definition's deployed BPMN
 * model literally declares, scoped to the exact {@code zeebe:input} field path each declaration
 * occupies. Unlike {@link ProcessDefinitionSecretKeyCache}'s {@code extractSecrets}, there is no
 * cross-input data-flow propagation: an intrinsic-function call is a complete literal written whole
 * into the one input that uses it (see
 * docs/superpowers/specs/2026-09-17-intrinsic-function-allow-list.md), never assembled across
 * inputs the way a secret's string value can be. See security-testing-findings#275.
 */
public class ProcessDefinitionIntrinsicFunctionAllowListCache {

  // Matches the literal FEEL/JSON-object-literal spelling connectors ship today, e.g.
  // {"camunda.function.type":"createGithubAppInstallationToken",...} written directly into a
  // zeebe:input's source text. Anchored to IntrinsicFunctionModel.DISCRIMINATOR_KEY's value rather
  // than a second hardcoded copy of the string.
  private static final Pattern DECLARATION =
      Pattern.compile(
          Pattern.quote("\"" + IntrinsicFunctionModel.DISCRIMINATOR_KEY + "\"")
              + "\\s*:\\s*\"(?<name>[\\p{Alnum}_]+)\"");

  private static final List<Class<? extends BaseElement>> OUTBOUND_ELIGIBLE_TYPES =
      List.of(
          ServiceTask.class,
          SendTask.class,
          ScriptTask.class,
          BusinessRuleTask.class,
          SubProcess.class,
          IntermediateThrowEvent.class,
          EndEvent.class);

  private final String physicalTenantId;
  private final ProcessDefinitionModelCache modelCache;
  private final Cache cache;

  public ProcessDefinitionIntrinsicFunctionAllowListCache(
      String physicalTenantId, ProcessDefinitionModelCache modelCache, Cache cache) {
    this.physicalTenantId = physicalTenantId;
    this.modelCache = modelCache;
    this.cache = cache;
  }

  private record CachedProcessDefinitionKey(String physicalTenantId, long processDefinitionKey) {}

  public List<AllowedIntrinsicFunction> getAllowedFunctions(
      IntrinsicFunctionAllowListContext context) {
    var cacheKey = new CachedProcessDefinitionKey(physicalTenantId, context.processDefinitionKey());
    Map<String, List<AllowedIntrinsicFunction>> byElement =
        cache.get(
            cacheKey,
            () ->
                extractAllowedFunctionsByElementId(
                    context.processDefinitionKey(), context.deadline()));
    return byElement.getOrDefault(context.elementId(), Collections.emptyList());
  }

  private Map<String, List<AllowedIntrinsicFunction>> extractAllowedFunctionsByElementId(
      long processDefinitionKey, Instant deadline) {
    var model = modelCache.getModel(processDefinitionKey, deadline);
    var processes = model.getDefinitions().getChildElementsByType(Process.class).stream().toList();
    Map<String, List<AllowedIntrinsicFunction>> result = new HashMap<>();
    for (Process process : processes) {
      for (BaseElement element : eligibleElements(process)) {
        result.put(element.getId(), extractFromInputs(findInputs(element)));
      }
    }
    return result;
  }

  private List<AllowedIntrinsicFunction> extractFromInputs(List<ZeebeInput> inputs) {
    List<AllowedIntrinsicFunction> result = new ArrayList<>();
    for (ZeebeInput input : inputs) {
      List<String> path = Arrays.asList(input.getTarget().split("\\."));
      var matcher = DECLARATION.matcher(input.getSource() == null ? "" : input.getSource());
      while (matcher.find()) {
        result.add(new AllowedIntrinsicFunction(matcher.group("name"), path));
      }
    }
    return result;
  }

  private List<ZeebeInput> findInputs(BaseElement element) {
    ZeebeIoMapping mapping = element.getSingleExtensionElement(ZeebeIoMapping.class);
    return mapping == null ? Collections.emptyList() : mapping.getInputs().stream().toList();
  }

  private Collection<BaseElement> eligibleElements(Process process) {
    Collection<FlowElement> all = collectFlowElements(process.getFlowElements(), new HashSet<>());
    Collection<BaseElement> eligible = new HashSet<>();
    for (FlowElement element : all) {
      OUTBOUND_ELIGIBLE_TYPES.forEach(
          type -> {
            if (type.isInstance(element)) {
              eligible.add(element);
            }
          });
    }
    return eligible;
  }

  private Collection<FlowElement> collectFlowElements(
      Collection<FlowElement> elements, Collection<FlowElement> buffer) {
    for (FlowElement element : elements) {
      if (element instanceof SubProcess subprocess) {
        buffer.add(subprocess);
        collectFlowElements(subprocess.getFlowElements(), buffer);
        continue;
      }
      buffer.add(element);
    }
    return buffer;
  }
}
