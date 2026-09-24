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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.connector.runtime.core.intrinsic.AllowedIntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListContext;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessDefinitionIntrinsicFunctionAllowListCacheTest {

  private static final String GITHUB_STYLE_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                        xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                        id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
        <bpmn:process id="proc" isExecutable="true">
          <bpmn:serviceTask id="github_task" name="GitHub">
            <bpmn:extensionElements>
              <zeebe:ioMapping>
                <zeebe:input source="=githubPat" target="fallbackToken" />
                <zeebe:input
                    source="={&quot;camunda.function.type&quot;:&quot;createGithubAppInstallationToken&quot;,&quot;params&quot;:[key]}"
                    target="authentication.token" />
                <zeebe:input source="={probeResult: hookResult.request.body.probe}" target="body" />
              </zeebe:ioMapping>
            </bpmn:extensionElements>
          </bpmn:serviceTask>
        </bpmn:process>
      </bpmn:definitions>
      """;

  // Mirrors the Microsoft 365 Mail connector's real sendMail attachments shape: the "body" input's
  // FEEL source declares base64 three levels under its own target -- "body" -> "message" ->
  // "attachments" (array, adds no segment of its own) -> "contentBytes" -- not at "body" itself.
  private static final String NESTED_ATTACHMENT_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                        xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                        id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
        <bpmn:process id="proc" isExecutable="true">
          <bpmn:serviceTask id="mail_task" name="Mail">
            <bpmn:extensionElements>
              <zeebe:ioMapping>
                <zeebe:input
                    source="={&quot;message&quot;:{&quot;attachments&quot;: for document in attachments return {&quot;contentBytes&quot;:{&quot;camunda.function.type&quot;:&quot;base64&quot;,&quot;params&quot;:[document]}}}}"
                    target="body" />
              </zeebe:ioMapping>
            </bpmn:extensionElements>
          </bpmn:serviceTask>
        </bpmn:process>
      </bpmn:definitions>
      """;

  private ProcessDefinitionModelCache modelCacheReturning(String xml) {
    var modelCache = mock(ProcessDefinitionModelCache.class);
    var model = Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));
    when(modelCache.getModel(eq(42L), any())).thenReturn(model);
    return modelCache;
  }

  @Test
  void aLiterallyDeclaredFunctionCallIsAllowedAtItsOwnFieldPath() {
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(GITHUB_STYLE_XML), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(
                42L, "github_task", Instant.now().plusSeconds(30)));

    assertThat(allowed)
        .contains(
            new AllowedIntrinsicFunction(
                "createGithubAppInstallationToken", List.of("authentication", "token")));
  }

  @Test
  void aDynamicIoMappingWithNoLiteralDeclarationContributesNoAllowedFunction() {
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(GITHUB_STYLE_XML), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(
                42L, "github_task", Instant.now().plusSeconds(30)));

    assertThat(allowed).noneMatch(a -> a.fieldPath().equals(List.of("body")));
  }

  @Test
  void anElementWithNoIoMappingAtAllReturnsAnEmptyList() {
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(GITHUB_STYLE_XML), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(
                42L, "no_such_element", Instant.now().plusSeconds(30)));

    assertThat(allowed).isEmpty();
  }

  @Test
  void aFunctionCallNestedInsideAContextAndListLiteralIsAllowedAtItsFullNestedPath() {
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(NESTED_ATTACHMENT_XML), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "mail_task", Instant.now().plusSeconds(30)));

    assertThat(allowed)
        .contains(
            new AllowedIntrinsicFunction(
                "base64", List.of("body", "message", "attachments", "contentBytes")));
  }

  @Test
  void aConcatenatedDiscriminatorValueIsNotRecordedAsAnAllowedDeclaration() {
    // A dynamic prefix (a process-variable reference the scanner cannot evaluate) makes the real
    // bound function name only as fixed as that variable's runtime value -- not the literal
    // "createLink" text this source happens to end with. Without requiring the discriminator's
    // value to be an immediate, standalone string literal, the scanner would misread this as a
    // fixed declaration of "createLink".
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="={&quot;camunda.function.type&quot;: attackerPrefix + &quot;createLink&quot;,&quot;params&quot;:[]}"
                      target="body" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed).isEmpty();
  }

  @Test
  void aDiscriminatorInsideACommentIsNotRecordedAsAnAllowedDeclaration() {
    // A "//" or "/* */" comment is opaque FEEL text, not a live literal -- text that merely
    // mentions the discriminator inside a comment must not grant anything.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="=/* {&quot;camunda.function.type&quot;:&quot;createLink&quot;} */ hookResult"
                      target="body" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed).isEmpty();
  }

  @Test
  void aBareUnquotedContextKeyStillComputesTheCorrectNestedPath() {
    // FEEL context literals commonly use bare (unquoted) keys, e.g. {result: {...}} rather than
    // {"result": {...}}. The declaration must still be recorded at "result", not at the input's
    // own target -- the exact-path invariant depends on it being scoped correctly either way.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="={result: {&quot;camunda.function.type&quot;: &quot;base64&quot;, &quot;params&quot;:[content]}}"
                      target="body" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed)
        .containsExactly(new AllowedIntrinsicFunction("base64", List.of("body", "result")));
  }

  @Test
  void aConditionalDeclarationIsGrantedEvenWhenTheOtherBranchIsNotAVerifiableShape() {
    // Mirrors the shipped GitHub template's actual auth-token binding exactly: the "then" branch
    // declares createGithubAppInstallationToken as a literal, but the "else" branch is a bare
    // reference to "githubPat" -- a separate, possibly process-controlled input's bound value.
    // security-testing-findings#275 follow-up on PR #8991 (camunda/connectors#9046): the
    // cross-branch check that used to reject this exact shape was reverted -- it broke this
    // shipped template without protecting against a real case, since createLink (the one
    // function whose params can reference another tenant's data) is not declared by any shipped
    // template, and every other function only acts on values the declaring branch's own author
    // wrote. The declaration is now granted regardless of the other branch's shape.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input source="=githubPat" target="githubPat" />
                  <zeebe:input
                      source="=if githubAuthType = &quot;github_app&quot; then {&quot;camunda.function.type&quot;:&quot;createGithubAppInstallationToken&quot;,&quot;params&quot;:[key]} else githubPat"
                      target="authentication.token" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed)
        .containsExactly(
            new AllowedIntrinsicFunction(
                "createGithubAppInstallationToken", List.of("authentication", "token")));
  }

  @Test
  void aConditionalDeclarationIsStillGrantedWhenBothBranchesAreVerifiableShapes() {
    // The counterpart to the test above: a plain string fallback (not a bare reference) is itself
    // a verifiable, fixed shape, so this legitimate declaration -- the exact form
    // HttpTests#intrinsicFunctionBase64InBearerToken exercises end-to-end -- must still be granted.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="=if true then {&quot;camunda.function.type&quot;:&quot;base64&quot;,&quot;params&quot;:[&quot;Hello World&quot;]} else &quot;fallback&quot;"
                      target="authentication.token" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed)
        .containsExactly(
            new AllowedIntrinsicFunction("base64", List.of("authentication", "token")));
  }

  @Test
  void aDeclarationIsNotGrantedWhenAnOpaqueSiblingArrayElementSharesItsPath() {
    // Array elements never push a further path segment, so every element competes for the exact
    // same bound path -- exactly like a conditional's branches. If one element is a literal
    // declaring createLink and a sibling element is a bare reference, the runtime walk cannot tell
    // which array index actually produced the value it finds there; the bare reference could, at
    // runtime, independently evaluate to something matching the declared shape.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="=[{&quot;camunda.function.type&quot;:&quot;createLink&quot;,&quot;params&quot;:[]}, attackerValue]"
                      target="body" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed).isEmpty();
  }

  @Test
  void aDeclarationIsGrantedEvenWhenAnOpaqueValueSharesItsPathInASiblingBranch() {
    // "then"'s own "x" key holds an opaque, process-controlled reference ("payload") at the exact
    // path "else" declares createLink at. security-testing-findings#275 follow-up on PR #8991
    // (camunda/connectors#9046): reverted -- a conditional's branches no longer reconcile against
    // each other, so "else"'s declaration is granted regardless of what "then" holds at the same
    // path.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="=if flag then {x: payload} else {x: {&quot;camunda.function.type&quot;:&quot;createLink&quot;,&quot;params&quot;:[]}}"
                      target="body" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed)
        .containsExactly(new AllowedIntrinsicFunction("createLink", List.of("body", "x")));
  }

  @Test
  void aDeclarationWrappedInAFunctionCallContributesNoGrant() {
    // append([], {...}) demonstrates why this parser refuses to guess: naive comma/bracket
    // tracking would clear key state at the comma between append's own arguments and record the
    // call at the input's own target instead of "outer" -- a wrong, shallower, attacker-reachable
    // grant. A construct this parser does not model must contribute nothing, not a wrong path.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="={&quot;outer&quot;: append([], {&quot;camunda.function.type&quot;:&quot;createLink&quot;,&quot;params&quot;:[]})}"
                      target="body" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed).isEmpty();
  }

  @Test
  void aDeclarationIsNotGrantedWhenAnEarlierDuplicateContextKeyIsOverriddenByALaterOpaqueOne() {
    // FEEL permits duplicate context keys, evaluated in order with the later entry overriding the
    // earlier one -- unlike a conditional's branches or a list's elements (reconciled via
    // mergeSiblings), a straight-line parse of one context literal records both the earlier
    // declaration and the later opaque marking directly into the same shared found/opaque sets,
    // with nothing to reconcile them against each other before returning. Without that final
    // reconciliation, the discarded "x" declaration below would survive even though "x"'s actual
    // runtime value comes from the later, process-controlled duplicate key.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="={x: {&quot;camunda.function.type&quot;:&quot;createLink&quot;,&quot;params&quot;:[]}, x: attackerValue}"
                      target="body" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed).isEmpty();
  }

  @Test
  void aDeclarationIsGrantedEvenWhenASiblingBranchHasANonStandaloneDiscriminatorValue() {
    // The "else" branch's discriminator value is a bare identifier
    // (attackerControlledFunctionName), not an immediate string literal, so its own object
    // contributes no declaration -- the standalone-literal requirement inside a single object
    // literal is unchanged. What *did* change (security-testing-findings#275 follow-up on PR
    // #8991, camunda/connectors#9046): a conditional's branches no longer reconcile against each
    // other at all, so this no longer matters for the "then" branch's own declaration either way
    // -- it's granted regardless of what the "else" branch looks like.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="=if flag then {&quot;camunda.function.type&quot;:&quot;createLink&quot;,&quot;params&quot;:[]} else {&quot;camunda.function.type&quot;: attackerControlledFunctionName,&quot;params&quot;:[]}"
                      target="body" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed)
        .containsExactly(new AllowedIntrinsicFunction("createLink", List.of("body")));
  }

  @Test
  void aLiteralDeclarationIsNotGrantedWhenALaterInputOverwritesTheExactSameTargetPath() {
    // security-testing-findings#275, T3: zeebe:input mappings evaluate in declaration order, and a
    // later mapping whose target equals an earlier one's replaces whatever the earlier one
    // produced there. Without shadowing, this cache would grant a function at a path the runtime
    // tree no longer actually holds it at, letting a second, dynamic (attacker-reachable) input
    // silently inherit the earlier declaration's grant.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="={&quot;camunda.function.type&quot;:&quot;createLink&quot;,&quot;params&quot;:[]}"
                      target="body" />
                  <zeebe:input source="=attackerControlledValue" target="body" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed).isEmpty();
  }

  @Test
  void aNestedLiteralDeclarationIsNotGrantedWhenALaterInputOverwritesAShallowerParentPath() {
    // Same as above, but the later, dynamic input overwrites a shallower ancestor of the
    // declaration's own path -- replacing the whole subtree the declaration's object literal was
    // nested under, not just its exact path.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="={&quot;x&quot;: {&quot;camunda.function.type&quot;:&quot;createLink&quot;,&quot;params&quot;:[]}}"
                      target="body" />
                  <zeebe:input source="=attackerControlledValue" target="body.x" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed).isEmpty();
  }

  @Test
  void aLiteralDeclarationIsStillGrantedWhenALaterInputOnlyWritesADeeperSiblingPath() {
    // A later input writing beneath the declaration's own path only adds a sibling field to the
    // object that already carries the discriminator literal -- it cannot overwrite the
    // "camunda.function.type" key itself (a zeebe:input target's dotted segments address nested
    // map paths, never the literal's own single, dotted-named object key), so the earlier
    // declaration is still trustworthy. This locks in the shadowing fix's deliberately narrower
    // scope: it only covers a later input writing to the exact same (or a shallower) path.
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" isExecutable="true">
            <bpmn:serviceTask id="task" name="Task">
              <bpmn:extensionElements>
                <zeebe:ioMapping>
                  <zeebe:input
                      source="={&quot;camunda.function.type&quot;:&quot;createLink&quot;,&quot;params&quot;:[]}"
                      target="body" />
                  <zeebe:input source="=someExtra" target="body.extra" />
                </zeebe:ioMapping>
              </bpmn:extensionElements>
            </bpmn:serviceTask>
          </bpmn:process>
        </bpmn:definitions>
        """;
    var cache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            modelCacheReturning(xml), Caffeine.newBuilder().build());

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed)
        .containsExactly(new AllowedIntrinsicFunction("createLink", List.of("body")));
  }
}
