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
import java.util.Set;
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

  /**
   * {@code zeebe:input} mappings evaluate in declaration order, each against the output the ones
   * before it already built, so a later mapping whose target is at or above an earlier one's
   * <em>replaces</em> whatever that earlier mapping produced there -- the same "effective writer"
   * semantics {@code ProcessDefinitionSecretKeyCache} already applies for secrets. A declaration is
   * therefore trusted only if no later input's target is a prefix of (or equal to) its own full
   * bound path: a later, dynamic write to the same path is exactly as capable of producing an
   * attacker-shaped value there as any other unverifiable position this cache already refuses to
   * trust, and the earlier declaration was never what the runtime tree actually ends up holding.
   * Unlike the secrets cache, this does not follow a later input's own FEEL expression back to an
   * earlier input it merely *reads* (rather than overwrites) -- that is a separate, tracked
   * follow-up; this only covers a later input writing to the exact same (or a shallower) path.
   */
  private List<AllowedIntrinsicFunction> extractFromInputs(List<ZeebeInput> inputs) {
    List<List<String>> targetPaths = new ArrayList<>();
    List<List<Declaration>> declarationsByInput = new ArrayList<>();
    for (ZeebeInput input : inputs) {
      targetPaths.add(Arrays.asList(input.getTarget().split("\\.")));
      declarationsByInput.add(findDeclarations(input.getSource()));
    }
    List<AllowedIntrinsicFunction> result = new ArrayList<>();
    for (int idx = 0; idx < inputs.size(); idx++) {
      List<String> targetPath = targetPaths.get(idx);
      for (Declaration declaration : declarationsByInput.get(idx)) {
        List<String> fullPath = new ArrayList<>(targetPath);
        fullPath.addAll(declaration.path());
        if (isShadowedByALaterInput(fullPath, targetPaths, idx + 1)) {
          continue;
        }
        result.add(new AllowedIntrinsicFunction(declaration.functionName(), fullPath));
      }
    }
    return result;
  }

  private static boolean isShadowedByALaterInput(
      List<String> fullPath, List<List<String>> targetPaths, int fromIndexInclusive) {
    for (int later = fromIndexInclusive; later < targetPaths.size(); later++) {
      List<String> laterTarget = targetPaths.get(later);
      if (laterTarget.size() <= fullPath.size()
          && fullPath.subList(0, laterTarget.size()).equals(laterTarget)) {
        return true;
      }
    }
    return false;
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
   *
   * <p>A declaration is trustworthy only if nothing else that could occupy the exact same bound
   * path is itself unverifiable. This still applies to a list literal's elements (an array never
   * pushes a further path segment, so every element competes for the same path) and to a
   * straight-line context literal's duplicate keys: every alternative is parsed into its own
   * path-tagged set of "opaque" positions before any of their declarations are trusted, and a
   * declaration is discarded if its own path is at or beneath any opaque position contributed by
   * <em>any</em> sibling alternative. It deliberately does <b>not</b> apply across a conditional's
   * {@code then}/{@code else} branches -- see {@link FeelLiteralParser#parseIfThenElse} for why.
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
    Set<List<String>> opaque = new HashSet<>();
    boolean ok = parser.parseValue(List.of(), found, opaque);
    if (!ok || !parser.atEnd()) {
      return List.of();
    }
    // FEEL permits duplicate context keys, evaluated in order with the later entry overriding the
    // earlier one -- e.g. {x: {"camunda.function.type":"createLink",...}, x: attackerValue}. A
    // straight-line parse (unlike a list's elements) records both the earlier declaration and the
    // later opaque marking directly into these same shared `found` and `opaque` sets, with no
    // equivalent of mergeSiblings to reconcile them against each other afterward. This final
    // filter is that reconciliation, applied once over the whole parse.
    return found.stream()
        .filter(d -> opaque.stream().noneMatch(op -> FeelLiteralParser.isPrefixOf(op, d.path())))
        .toList();
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

    /**
     * Parses the value at the current position into {@code path}-tagged declarations and opaque
     * positions. A quoted string, a number, and {@code true}/{@code false}/{@code null} are
     * immutable scalars -- always exactly what they say, regardless of any surrounding process data
     * -- so parsing them never adds to {@code opaque}. Anything this parser has no further grammar
     * for (a bare identifier, a function call, an operator expression) could evaluate to any JSON
     * value at runtime, so {@code path} itself is recorded as opaque.
     */
    boolean parseValue(List<String> path, List<Declaration> found, Set<List<String>> opaque) {
      char c = peek();
      if (c == '{') {
        return parseContextLiteral(path, found, opaque);
      }
      if (c == '[') {
        return parseListLiteral(path, found, opaque);
      }
      if (c == '"') {
        skipQuotedSpan();
        return true;
      }
      if (c == '-' || Character.isDigit(c)) {
        skipNumberLiteral();
        return true;
      }
      if (tryConsumeKeyword("true") || tryConsumeKeyword("false") || tryConsumeKeyword("null")) {
        return true;
      }
      if (matchesKeywordAt("if")) {
        i += 2;
        return parseIfThenElse(path, found, opaque);
      }
      if (matchesKeywordAt("for")) {
        i += 3;
        return parseForReturn(path, found, opaque);
      }
      opaque.add(path);
      return skipOpaqueValue();
    }

    private boolean parseContextLiteral(
        List<String> path, List<Declaration> found, Set<List<String>> opaque) {
      i++; // consume '{'
      if (tryConsumeChar('}')) {
        return true;
      }
      while (true) {
        String key = parseKey();
        if (key == null || !tryConsumeChar(':')) {
          return false;
        }
        if (IntrinsicFunctionModel.DISCRIMINATOR_KEY.equals(key)) {
          int save = i;
          boolean standalone = false;
          String value = null;
          if (peek() == '"') {
            value = parseStringLiteral();
            char after = peek();
            standalone = after == ',' || after == '}';
          }
          if (standalone) {
            found.add(new Declaration(value, path));
          } else {
            // Not an immediate, standalone string literal -- a bare reference, a concatenation, a
            // number, anything else. The object's own shape at this path is therefore not fixed
            // either: whether this object even carries the discriminator name a sibling
            // alternative's literal declares is itself unverifiable, so the object's own path (not
            // a sub-path under the discriminator key, which no declaration is ever recorded at
            // anyway) is what must be marked opaque for mergeSiblings to catch it.
            i = save;
            opaque.add(path);
            if (!skipOpaqueValue()) {
              return false;
            }
          }
        } else if (!parseValue(appendKey(path, key), found, opaque)) {
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

    /**
     * Every element shares the array's own path -- an array never pushes a further path segment,
     * matching {@link IntrinsicFunctionUtil}'s walk -- so sibling elements are exactly as much in
     * competition for that path as a duplicate context key is: each element is parsed into its own
     * temporary (declarations, opaque) pair first, then a declaration from any element is kept only
     * if no element's opaque set (its own, or any other element's) reaches at or beneath that
     * declaration's path.
     */
    private boolean parseListLiteral(
        List<String> path, List<Declaration> found, Set<List<String>> opaque) {
      i++; // consume '['
      if (tryConsumeChar(']')) {
        return true;
      }
      List<List<Declaration>> perElementFound = new ArrayList<>();
      List<Set<List<String>>> perElementOpaque = new ArrayList<>();
      while (true) {
        List<Declaration> elementFound = new ArrayList<>();
        Set<List<String>> elementOpaque = new HashSet<>();
        if (!parseValue(path, elementFound, elementOpaque)) {
          return false;
        }
        perElementFound.add(elementFound);
        perElementOpaque.add(elementOpaque);
        if (tryConsumeChar(',')) {
          continue;
        }
        if (tryConsumeChar(']')) {
          break;
        }
        return false;
      }
      mergeSiblings(perElementFound, perElementOpaque, found, opaque);
      return true;
    }

    /**
     * A declaration in either branch of a conditional is granted unconditionally, regardless of
     * whether the other branch is itself a verifiable shape. This is a deliberate, accepted
     * relaxation (security-testing-findings#275 follow-up on PR #8991, tracked for documentation in
     * camunda/connectors#9046): the cross-branch opacity check this replaced (which used to route
     * through {@link #mergeSiblings}, the same way {@link #parseListLiteral} still does) rejected
     * the shipped GitHub template's own {@code createGithubAppInstallationToken} auth-mode
     * conditional and its base64-upload conditional, in both cases because the *other* branch was a
     * bare reference to a separate input this parser has no way to verify. In practice this check
     * was never protecting against the case it was built for: {@code createLink} -- the one
     * intrinsic function whose parameters can be pointed at another tenant's/process's data -- is
     * not declared by any shipped element template today, and every other intrinsic function
     * (base64, getText, getJson, createGithubAppInstallationToken) only transforms or mints
     * credentials scoped to values the template author already wrote into that same branch, so an
     * attacker steering which branch runs gains nothing beyond what that branch's own author put
     * there. Neither branch's own opaque positions are surfaced to an enclosing construct either:
     * this conditional, once parsed, is fully resolved from the perspective of anything wrapping it
     * (a list element, a duplicate key, an outer conditional).
     */
    private boolean parseIfThenElse(
        List<String> path, List<Declaration> found, Set<List<String>> opaque) {
      if (!skipUntilKeyword("then") || !tryConsumeKeyword("then")) {
        return false;
      }
      List<Declaration> thenFound = new ArrayList<>();
      Set<List<String>> thenOpaque = new HashSet<>();
      if (!parseValue(path, thenFound, thenOpaque)) {
        return false;
      }
      if (!tryConsumeKeyword("else")) {
        return false;
      }
      List<Declaration> elseFound = new ArrayList<>();
      Set<List<String>> elseOpaque = new HashSet<>();
      if (!parseValue(path, elseFound, elseOpaque)) {
        return false;
      }
      found.addAll(thenFound);
      found.addAll(elseFound);
      return true;
    }

    /**
     * Combines declarations and opaque positions gathered independently from several mutually
     * exclusive alternatives (a list literal's elements -- a conditional's branches no longer go
     * through this, see {@link #parseIfThenElse}): every alternative's opaque positions are unioned
     * first, then a declaration from any alternative is added to {@code found} only if no unioned
     * opaque position is a prefix of (at, or beneath) that declaration's own path -- and the
     * unioned set is always propagated to {@code opaque}, so an enclosing conditional or list sees
     * this whole construct's unverifiable positions too.
     */
    private static void mergeSiblings(
        List<List<Declaration>> perAlternativeFound,
        List<Set<List<String>>> perAlternativeOpaque,
        List<Declaration> found,
        Set<List<String>> opaque) {
      Set<List<String>> allOpaque = new HashSet<>();
      for (Set<List<String>> s : perAlternativeOpaque) {
        allOpaque.addAll(s);
      }
      for (List<Declaration> alternativeFound : perAlternativeFound) {
        for (Declaration d : alternativeFound) {
          if (allOpaque.stream().noneMatch(op -> isPrefixOf(op, d.path()))) {
            found.add(d);
          }
        }
      }
      opaque.addAll(allOpaque);
    }

    private static boolean isPrefixOf(List<String> prefix, List<String> path) {
      return prefix.size() <= path.size() && path.subList(0, prefix.size()).equals(prefix);
    }

    private boolean parseForReturn(
        List<String> path, List<Declaration> found, Set<List<String>> opaque) {
      // Everything between "for" and "return" (one or more "ident in <expr>" iterators,
      // comma-separated) is opaque to this parser; only the returned value matters, and it shares
      // the for-loop's own path -- a for-loop's result is a list, which never pushes a segment.
      // There is only one return template applied to every iteration, so there is no sibling
      // alternative to reconcile against here.
      if (!skipUntilKeyword("return") || !tryConsumeKeyword("return")) {
        return false;
      }
      return parseValue(path, found, opaque);
    }

    private void skipNumberLiteral() {
      if (i < n && s.charAt(i) == '-') {
        i++;
      }
      while (i < n && Character.isDigit(s.charAt(i))) {
        i++;
      }
      if (i < n && s.charAt(i) == '.') {
        i++;
        while (i < n && Character.isDigit(s.charAt(i))) {
          i++;
        }
      }
      if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
        int mark = i;
        i++;
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
          i++;
        }
        if (i < n && Character.isDigit(s.charAt(i))) {
          while (i < n && Character.isDigit(s.charAt(i))) {
            i++;
          }
        } else {
          i = mark;
        }
      }
    }

    /**
     * Skips one value this parser does not otherwise recognize -- a bare variable reference, or an
     * arbitrary function-call/operator expression -- without recording anything from within it,
     * stopping at the first unmatched {@code ,}/{@code then}/{@code else} or an unmatched closing
     * {@code }}/{@code ]}/{@code )} at this value's own nesting depth.
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
