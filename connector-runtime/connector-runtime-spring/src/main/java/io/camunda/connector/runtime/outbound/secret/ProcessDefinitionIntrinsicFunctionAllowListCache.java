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
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionUtil;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.cache.Cache;

/**
 * Statically finds every {@code camunda.function.type} call a process definition's deployed BPMN
 * model literally declares, scoped to the exact field path each declaration occupies once bound
 * (the {@code zeebe:input}'s own target, plus however deep the call sits inside that input's FEEL
 * object/array literal). Unlike {@link ProcessDefinitionSecretKeyCache}'s {@code extractSecrets},
 * there is no cross-input data-flow propagation: an intrinsic-function call is a complete literal
 * written whole into the one input that uses it, never assembled across inputs the way a secret's
 * string value can be. See security-testing-findings#275.
 */
public class ProcessDefinitionIntrinsicFunctionAllowListCache {

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
      List<String> targetPath = Arrays.asList(input.getTarget().split("\\."));
      for (Declaration declaration : findDeclarations(input.getSource())) {
        List<String> fullPath = new ArrayList<>(targetPath);
        fullPath.addAll(declaration.path());
        result.add(new AllowedIntrinsicFunction(declaration.functionName(), fullPath));
      }
    }
    return result;
  }

  private record Declaration(String functionName, List<String> path) {}

  private record Frame(List<String> path, boolean isObject) {}

  /**
   * Scans a {@code zeebe:input}'s FEEL source text for {@code {"camunda.function.type":"name",
   * ...}} object literals, returning each one's path -- the chain of enclosing object-literal keys
   * -- relative to this input's own target. A template can nest a call arbitrarily deep inside
   * context/list literals (for example an email attachment's {@code contentBytes}, several levels
   * under the input's own target), so the declared path must be computed from the literal's actual
   * structure rather than assumed to equal the input's target alone.
   *
   * <p>Mirrors {@link IntrinsicFunctionUtil}'s bound-tree walk exactly: a path segment is pushed
   * only when descending from an object literal into the value of one of its keys -- whether that
   * value is itself an object, an array, or a scalar -- and an array's own elements do not push a
   * further segment. FEEL control-flow keywords ({@code if}/{@code then}/{@code else}/{@code
   * for}/{@code in}/{@code return}), {@code //} and {@code /* *&#47;} comments, and everything else
   * outside of {@code {}}, {@code []}, quoted strings and the {@code :}/{@code ,} separators is
   * skipped as opaque text; none of it corresponds to an object-literal key.
   *
   * <p>The discriminator's own value is recorded as a declaration only when it is an immediate,
   * standalone string literal -- nothing but whitespace between the {@code :} and the opening
   * {@code "}, and nothing but whitespace between the closing {@code "} and the entry's terminating
   * {@code ,}/{@code }}/{@code ]}. This scanner has no FEEL grammar of its own, so without that
   * check a computed value such as {@code attackerPrefix + "createLink"} would read as if {@code
   * "createLink"} were the whole, fixed value the model declares, when the real bound value is only
   * as fixed as {@code attackerPrefix} -- process-controlled -- allows.
   */
  private static List<Declaration> findDeclarations(String source) {
    if (source == null || source.isEmpty()) {
      return List.of();
    }
    List<Declaration> found = new ArrayList<>();
    Deque<Frame> frames = new ArrayDeque<>();
    frames.push(new Frame(List.of(), false));
    String pendingKey = null;
    boolean pendingKeyValueIsBareSoFar = false;
    Declaration pendingCandidate = null;

    int i = 0;
    int n = source.length();
    while (i < n) {
      char c = source.charAt(i);
      if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
        pendingCandidate = null;
        while (i < n && source.charAt(i) != '\n') {
          i++;
        }
        continue;
      }
      if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
        pendingCandidate = null;
        i += 2;
        while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
          i++;
        }
        i = Math.min(i + 2, n);
        continue;
      }
      if (pendingCandidate != null && !Character.isWhitespace(c)) {
        if (c == ',' || c == '}' || c == ']') {
          found.add(pendingCandidate);
        }
        pendingCandidate = null;
      }
      if (c == '"') {
        int j = i + 1;
        StringBuilder text = new StringBuilder();
        while (j < n && source.charAt(j) != '"') {
          char ch = source.charAt(j);
          if (ch == '\\' && j + 1 < n) {
            text.append(source.charAt(j + 1));
            j += 2;
          } else {
            text.append(ch);
            j++;
          }
        }
        i = j + 1;

        Frame top = frames.peek();
        if (top.isObject() && pendingKey == null) {
          int k = i;
          while (k < n && Character.isWhitespace(source.charAt(k))) {
            k++;
          }
          if (k < n && source.charAt(k) == ':') {
            pendingKey = text.toString();
            pendingKeyValueIsBareSoFar = true;
            i = k + 1;
          }
        } else if (pendingKey != null) {
          if (pendingKeyValueIsBareSoFar
              && IntrinsicFunctionModel.DISCRIMINATOR_KEY.equals(pendingKey)) {
            pendingCandidate = new Declaration(text.toString(), top.path());
          }
          pendingKey = null;
        }
        continue;
      }
      if (c == '{' || c == '[') {
        Frame parent = frames.peek();
        List<String> childPath = parent.path();
        if (pendingKey != null) {
          childPath = new ArrayList<>(parent.path());
          childPath.add(pendingKey);
          pendingKey = null;
        }
        frames.push(new Frame(childPath, c == '{'));
        i++;
        continue;
      }
      if (c == '}' || c == ']') {
        if (frames.size() > 1) {
          frames.pop();
        }
        pendingKey = null;
        i++;
        continue;
      }
      if (c == ',') {
        pendingKey = null;
        i++;
        continue;
      }
      if (pendingKey != null && !Character.isWhitespace(c)) {
        pendingKeyValueIsBareSoFar = false;
      }
      i++;
    }
    return found;
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
