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

import io.camunda.connector.runtime.core.intrinsic.AllowedIntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListContext;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

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
            "tenant-a",
            modelCacheReturning(GITHUB_STYLE_XML),
            new ConcurrentMapCache("allow-list"));

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
            "tenant-a",
            modelCacheReturning(GITHUB_STYLE_XML),
            new ConcurrentMapCache("allow-list"));

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
            "tenant-a",
            modelCacheReturning(GITHUB_STYLE_XML),
            new ConcurrentMapCache("allow-list"));

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
            "tenant-a",
            modelCacheReturning(NESTED_ATTACHMENT_XML),
            new ConcurrentMapCache("allow-list"));

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
            "tenant-a", modelCacheReturning(xml), new ConcurrentMapCache("allow-list"));

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
            "tenant-a", modelCacheReturning(xml), new ConcurrentMapCache("allow-list"));

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
            "tenant-a", modelCacheReturning(xml), new ConcurrentMapCache("allow-list"));

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed)
        .containsExactly(new AllowedIntrinsicFunction("base64", List.of("body", "result")));
  }

  @Test
  void aConditionalDeclarationIsNotGrantedWhenTheOtherBranchIsNotAVerifiableShape() {
    // Mirrors the shipped GitHub template's actual auth-token binding exactly: the "then" branch
    // declares createGithubAppInstallationToken as a literal, but the "else" branch is a bare
    // reference to "githubPat" -- a separate, possibly process-controlled input's bound value. If
    // the "else" branch is active at runtime (PAT auth mode), the field's real value is whatever
    // that other input produced -- which could, in principle, itself be crafted to match this
    // declared (function, path) shape. Neither branch's declaration can be trusted, since which one
    // actually reached this field at runtime cannot be recovered once the tree is bound.
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
            "tenant-a", modelCacheReturning(xml), new ConcurrentMapCache("allow-list"));

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed).isEmpty();
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
            "tenant-a", modelCacheReturning(xml), new ConcurrentMapCache("allow-list"));

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed)
        .containsExactly(
            new AllowedIntrinsicFunction("base64", List.of("authentication", "token")));
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
            "tenant-a", modelCacheReturning(xml), new ConcurrentMapCache("allow-list"));

    var allowed =
        cache.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(42L, "task", Instant.now().plusSeconds(30)));

    assertThat(allowed).isEmpty();
  }

  @Test
  void twoPhysicalTenantsSharingOneCacheDoNotLeakAllowedFunctionsBetweenEachOther() {
    var sharedCache = new ConcurrentMapCache("allow-list");
    var cacheForTenantA =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            "tenant-a", modelCacheReturning(GITHUB_STYLE_XML), sharedCache);
    var emptyModelCache = mock(ProcessDefinitionModelCache.class);
    when(emptyModelCache.getModel(eq(42L), any()))
        .thenReturn(
            Bpmn.readModelFromStream(
                new ByteArrayInputStream(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                      id="defs2" targetNamespace="http://bpmn.io/schema/bpmn">
                      <bpmn:process id="proc" isExecutable="true">
                        <bpmn:serviceTask id="github_task" name="Other" />
                      </bpmn:process>
                    </bpmn:definitions>
                    """
                        .getBytes())));
    var cacheForTenantB =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            "tenant-b", emptyModelCache, sharedCache);

    var allowedA =
        cacheForTenantA.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(
                42L, "github_task", Instant.now().plusSeconds(30)));
    var allowedB =
        cacheForTenantB.getAllowedFunctions(
            new IntrinsicFunctionAllowListContext(
                42L, "github_task", Instant.now().plusSeconds(30)));

    assertThat(allowedA).isNotEmpty();
    assertThat(allowedB).isEmpty();
  }
}
