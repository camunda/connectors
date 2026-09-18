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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

  /**
   * Parses a {@code zeebe:input}'s FEEL source text for {@code {"camunda.function.type":"name",
   * ...}} object literals, returning each one's path -- the chain of enclosing object-literal keys
   * -- relative to this input's own target. A template can nest a call arbitrarily deep inside
   * context/list literals (for example an email attachment's {@code contentBytes}, several levels
   * under the input's own target), so the declared path must be computed from the literal's actual
   * structure rather than assumed to equal the input's target alone.
   *
   * <p>This recognizes exactly the FEEL forms shipped templates actually use: context literals
   * (quoted or bare keys), list literals, {@code if}/{@code then}/{@code else}, and {@code for}/
   * {@code in}/{@code return} -- mirroring {@link IntrinsicFunctionUtil}'s bound-tree walk, where a
   * path segment is pushed only when descending into the value of an object key, and neither an
   * array's elements nor a {@code for}-loop's result push a further one. Anything else -- a
   * function call, an operator expression, a bare variable reference used as a value -- is skipped
   * as an opaque, unparsed span rather than recorded into or descended through: this scanner has no
   * full FEEL grammar, so a value it cannot structurally account for must contribute no grant,
   * rather than a grant at a guessed (and possibly wrong, shallower, attacker-reachable) path. A
   * discriminator's own value is likewise recorded only when it is an immediate, standalone string
   * literal -- a computed value such as {@code attackerPrefix + "createLink"} is only as fixed as
   * {@code attackerPrefix}, process-controlled, allows, so it is treated as opaque rather than as a
   * literal declaration of {@code "createLink"}.
   *
   * <p>A source that does not fully parse this way (a syntax this scanner does not model appears on
   * the direct path to a discriminator, or the input is not a {@code "="}-prefixed FEEL expression
   * at all) contributes no declarations at all for that input, rather than a partial result.
   */
  private static List<Declaration> findDeclarations(String rawSource) {
    if (rawSource == null) {
      return List.of();
    }
    String trimmed = rawSource.strip();
    if (!trimmed.startsWith("=")) {
      return List.of();
    }
    var parser = new FeelLiteralParser(trimmed.substring(1));
    List<Declaration> found = new ArrayList<>();
    boolean ok = parser.parseValue(List.of(), found);
    if (!ok || !parser.atEnd()) {
      return List.of();
    }
    return found;
  }

  /**
   * A minimal recursive-descent parser for the safe FEEL subset {@link #findDeclarations}
   * recognizes. See that method's javadoc for the grammar and the safety rationale.
   */
  private static final class FeelLiteralParser {

    private final String s;
    private final int n;
    private int i;

    FeelLiteralParser(String s) {
      this.s = s;
      this.n = s.length();
      this.i = 0;
    }

    boolean atEnd() {
      skipWs();
      return i >= n;
    }

    boolean parseValue(List<String> path, List<Declaration> found) {
      char c = peek();
      if (c == '{') {
        return parseContextLiteral(path, found);
      }
      if (c == '[') {
        return parseListLiteral(path, found);
      }
      if (matchesKeywordAt("if")) {
        i += 2;
        return parseIfThenElse(path, found);
      }
      if (matchesKeywordAt("for")) {
        i += 3;
        return parseForReturn(path, found);
      }
      return skipOpaqueValue();
    }

    private boolean parseContextLiteral(List<String> path, List<Declaration> found) {
      i++; // consume '{'
      if (tryConsumeChar('}')) {
        return true;
      }
      while (true) {
        String key = parseKey();
        if (key == null || !tryConsumeChar(':')) {
          return false;
        }
        if (IntrinsicFunctionModel.DISCRIMINATOR_KEY.equals(key) && peek() == '"') {
          int save = i;
          String value = parseStringLiteral();
          char after = peek();
          if (after == ',' || after == '}') {
            found.add(new Declaration(value, path));
          } else {
            i = save;
            if (!skipOpaqueValue()) {
              return false;
            }
          }
        } else if (!parseValue(appendKey(path, key), found)) {
          return false;
        }
        if (tryConsumeChar(',')) {
          continue;
        }
        if (tryConsumeChar('}')) {
          return true;
        }
        return false;
      }
    }

    private boolean parseListLiteral(List<String> path, List<Declaration> found) {
      i++; // consume '['
      if (tryConsumeChar(']')) {
        return true;
      }
      while (true) {
        // Array elements share the array's own path -- an array never pushes a further segment,
        // matching IntrinsicFunctionUtil's walk.
        if (!parseValue(path, found)) {
          return false;
        }
        if (tryConsumeChar(',')) {
          continue;
        }
        if (tryConsumeChar(']')) {
          return true;
        }
        return false;
      }
    }

    /**
     * A declaration in one branch of a conditional grants nothing unless <em>both</em> branches are
     * themselves statically verifiable shapes -- a literal, a context/list literal, or another
     * conditional recursively satisfying this same rule. If either branch is instead a bare
     * variable reference, a function call, or any other expression this parser cannot pin to a
     * fixed shape, that branch's real value at runtime could be anything -- including a value that
     * happens to match the other branch's declared {@code (functionName, path)} shape. The shipped
     * GitHub template hits exactly this: {@code if githubAuthType = "github_app" then
     * {"camunda.function.type":"createGithubAppInstallationToken", ...} else githubPat} declares
     * the call in the {@code then} branch, but {@code githubPat} (bound from a separate, possibly
     * process-controlled input) is not a verifiable shape, so neither branch's declarations are
     * trusted -- discarding both, rather than granting one based on which branch merely happens to
     * contain the literal text.
     */
    private boolean parseIfThenElse(List<String> path, List<Declaration> found) {
      if (!skipUntilKeyword("then") || !tryConsumeKeyword("then")) {
        return false;
      }
      boolean thenIsVerifiable = nextValueIsAVerifiableShape();
      List<Declaration> thenDeclarations = new ArrayList<>();
      if (!parseValue(path, thenDeclarations)) {
        return false;
      }
      if (!tryConsumeKeyword("else")) {
        return false;
      }
      boolean elseIsVerifiable = nextValueIsAVerifiableShape();
      List<Declaration> elseDeclarations = new ArrayList<>();
      if (!parseValue(path, elseDeclarations)) {
        return false;
      }
      if (thenIsVerifiable && elseIsVerifiable) {
        found.addAll(thenDeclarations);
        found.addAll(elseDeclarations);
      }
      return true;
    }

    /**
     * Whether the value at the current position is one this parser can pin to a fixed shape: a
     * context/list literal, a quoted string, a number, {@code true}/{@code false}/{@code null}, or
     * another conditional or {@code for} loop (each recursively verified the same way when parsed).
     * Anything else -- a bare identifier, a function call, an operator expression -- could evaluate
     * to any JSON value at runtime.
     */
    private boolean nextValueIsAVerifiableShape() {
      char c = peek();
      return c == '{'
          || c == '['
          || c == '"'
          || c == '-'
          || Character.isDigit(c)
          || matchesKeywordAt("if")
          || matchesKeywordAt("for")
          || matchesKeywordAt("true")
          || matchesKeywordAt("false")
          || matchesKeywordAt("null");
    }

    private boolean parseForReturn(List<String> path, List<Declaration> found) {
      // Everything between "for" and "return" (one or more "ident in <expr>" iterators,
      // comma-separated) is opaque to this parser; only the returned value matters, and it shares
      // the for-loop's own path -- a for-loop's result is a list, which never pushes a segment.
      if (!skipUntilKeyword("return") || !tryConsumeKeyword("return")) {
        return false;
      }
      return parseValue(path, found);
    }

    /**
     * Skips one value this parser does not otherwise recognize -- a number, boolean, null, string,
     * bare variable reference, or arbitrary function-call/operator expression -- without recording
     * anything from within it, stopping at the first unmatched {@code ,}/{@code then}/{@code else}
     * or an unmatched closing {@code }}/{@code ]}/{@code )} at this value's own nesting depth.
     */
    private boolean skipOpaqueValue() {
      int depth = 0;
      while (i < n) {
        if (skipWsOrCommentStep()) {
          continue;
        }
        char c = s.charAt(i);
        if (c == '"') {
          skipQuotedSpan();
          continue;
        }
        if (c == '{' || c == '[' || c == '(') {
          depth++;
          i++;
          continue;
        }
        if (c == '}' || c == ']' || c == ')') {
          if (depth == 0) {
            return true;
          }
          depth--;
          i++;
          continue;
        }
        if (depth == 0 && (c == ',' || matchesKeywordAt("then") || matchesKeywordAt("else"))) {
          return true;
        }
        i++;
      }
      return true;
    }

    /** Like {@link #skipOpaqueValue()}, but stops only at {@code stopKeyword} at depth 0. */
    private boolean skipUntilKeyword(String stopKeyword) {
      int depth = 0;
      while (i < n) {
        if (skipWsOrCommentStep()) {
          continue;
        }
        char c = s.charAt(i);
        if (c == '"') {
          skipQuotedSpan();
          continue;
        }
        if (c == '{' || c == '[' || c == '(') {
          depth++;
          i++;
          continue;
        }
        if (c == '}' || c == ']' || c == ')') {
          if (depth == 0) {
            return false;
          }
          depth--;
          i++;
          continue;
        }
        if (depth == 0 && matchesKeywordAt(stopKeyword)) {
          return true;
        }
        i++;
      }
      return false;
    }

    private String parseKey() {
      skipWs();
      if (i >= n) {
        return null;
      }
      if (s.charAt(i) == '"') {
        return parseStringLiteral();
      }
      if (isIdentifierStart(s.charAt(i))) {
        int start = i;
        i++;
        while (i < n && isIdentifierPart(s.charAt(i))) {
          i++;
        }
        return s.substring(start, i);
      }
      return null;
    }

    /** Assumes the current character is the opening {@code "}. */
    private String parseStringLiteral() {
      i++;
      StringBuilder text = new StringBuilder();
      while (i < n && s.charAt(i) != '"') {
        char ch = s.charAt(i);
        if (ch == '\\' && i + 1 < n) {
          text.append(s.charAt(i + 1));
          i += 2;
        } else {
          text.append(ch);
          i++;
        }
      }
      if (i < n) {
        i++;
      }
      return text.toString();
    }

    /** Assumes the current character is the opening {@code "}; discards the content. */
    private void skipQuotedSpan() {
      i++;
      while (i < n && s.charAt(i) != '"') {
        i += (s.charAt(i) == '\\' && i + 1 < n) ? 2 : 1;
      }
      if (i < n) {
        i++;
      }
    }

    private char peek() {
      skipWs();
      return i < n ? s.charAt(i) : '\0';
    }

    private boolean tryConsumeChar(char c) {
      skipWs();
      if (i < n && s.charAt(i) == c) {
        i++;
        return true;
      }
      return false;
    }

    private boolean tryConsumeKeyword(String keyword) {
      skipWs();
      if (!matchesKeywordAt(keyword)) {
        return false;
      }
      i += keyword.length();
      return true;
    }

    private boolean matchesKeywordAt(String keyword) {
      if (i + keyword.length() > n || !s.regionMatches(i, keyword, 0, keyword.length())) {
        return false;
      }
      if (i > 0 && isIdentifierPart(s.charAt(i - 1))) {
        return false;
      }
      int after = i + keyword.length();
      return after >= n || !isIdentifierPart(s.charAt(after));
    }

    private void skipWs() {
      while (skipWsOrCommentStep()) {
        // keep going
      }
    }

    /** Skips one whitespace run or one comment starting at the current position, if present. */
    private boolean skipWsOrCommentStep() {
      if (i < n && Character.isWhitespace(s.charAt(i))) {
        while (i < n && Character.isWhitespace(s.charAt(i))) {
          i++;
        }
        return true;
      }
      if (i + 1 < n && s.charAt(i) == '/' && s.charAt(i + 1) == '/') {
        while (i < n && s.charAt(i) != '\n') {
          i++;
        }
        return true;
      }
      if (i + 1 < n && s.charAt(i) == '/' && s.charAt(i + 1) == '*') {
        i += 2;
        while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
          i++;
        }
        i = Math.min(i + 2, n);
        return true;
      }
      return false;
    }

    private static boolean isIdentifierStart(char c) {
      return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
      return Character.isLetterOrDigit(c) || c == '_';
    }

    private static List<String> appendKey(List<String> path, String key) {
      List<String> extended = new ArrayList<>(path.size() + 1);
      extended.addAll(path);
      extended.add(key);
      return extended;
    }
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
