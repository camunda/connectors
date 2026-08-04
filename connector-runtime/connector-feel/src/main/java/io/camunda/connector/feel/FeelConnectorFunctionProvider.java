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
package io.camunda.connector.feel;

import io.camunda.connector.feel.function.BackoffFunction;
import io.camunda.connector.feel.function.BpmnErrorFunction;
import io.camunda.connector.feel.function.CreateDocumentFunction;
import io.camunda.connector.feel.function.IgnoreErrorFunction;
import io.camunda.connector.feel.function.JobErrorFunction;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.context.JavaFunctionProvider;

/** Provider of Connector-related FEEL functions like 'bpmnError'. */
public class FeelConnectorFunctionProvider extends JavaFunctionProvider {

  public static final String ERROR_TYPE_PROPERTY = "errorType";
  public static final String BPMN_ERROR_TYPE_VALUE = "bpmnError";
  public static final String JOB_ERROR_TYPE_VALUE = "jobError";
  public static final String IGNORE_ERROR_TYPE_VALUE = "ignoreError";

  /**
   * Key of the sentinel object {@code io.camunda.connector.feel.function.CreateDocumentFunction}
   * produces when {@code createDocument(...)} is called in a result/error expression — e.g. {@code
   * {"connectorResultFunction": "createDocument:<per-evaluation nonce>", "value": <argument>}}.
   * {@code io.camunda.connector.runtime.core.document.ResultDocumentResolver} (invoked from {@code
   * ConnectorResultHandler} right after FEEL evaluation) walks the evaluated JSON tree looking for
   * an object with this key set to the current evaluation's nonce (see {@link
   * #currentCreateDocumentNonce()}), and replaces each match with a real {@code Document} built via
   * {@code DocumentFactory}.
   */
  public static final String RESULT_FUNCTION_TYPE_PROPERTY = "connectorResultFunction";

  /**
   * Holds the nonce {@code CreateDocumentFunction} tags its sentinel with for the currently
   * evaluating result/error expression on this thread, and the exact value {@code
   * ResultDocumentResolver} compares against to recognize one — see {@link
   * #RESULT_FUNCTION_TYPE_PROPERTY} for how producer and consumer are wired together.
   *
   * <p>Deliberately scoped per evaluation (not a single per-JVM constant): a per-JVM value would be
   * a plain FEEL-visible string once produced — nothing stops an expression from projecting it back
   * out (e.g. {@code createDocument(x).connectorResultFunction}) — so any tenant able to author a
   * result/error expression on a shared runtime could learn it and later forge sentinels in another
   * tenant's response/request data. Scoping a fresh nonce to each evaluation means a value learned
   * during one evaluation is worthless against any other: {@link
   * #beginCreateDocumentEvaluationScope()} installs a fresh one before evaluation starts, and
   * {@link #currentCreateDocumentNonce()} lazily generates it on first use within that scope only —
   * so evaluations that never call {@code createDocument()} never pay the UUID generation cost.
   */
  private static final ThreadLocal<NonceHolder> CURRENT_NONCE = new ThreadLocal<>();

  /**
   * Opens a fresh {@code createDocument()} nonce scope for the calling thread. Callers must pair
   * this with a {@code finally}-block call to {@link #endCreateDocumentEvaluationScope()} once the
   * current result/error expression's evaluation AND document resolution have both completed, and
   * must call this before {@code FeelExpressionEvaluator#evaluateToJson} so {@code
   * CreateDocumentFunction} has an active scope to tag its sentinel from.
   */
  public static void beginCreateDocumentEvaluationScope() {
    CURRENT_NONCE.set(new NonceHolder());
  }

  /** Ends the calling thread's active {@code createDocument()} nonce scope, if any. */
  public static void endCreateDocumentEvaluationScope() {
    CURRENT_NONCE.remove();
  }

  /**
   * Returns the current thread's active scope nonce, generating it on first call within that scope.
   * Called both by {@code CreateDocumentFunction} (to tag a new sentinel) and by {@code
   * ResultDocumentResolver} (to recognize one) — see {@link #CURRENT_NONCE}.
   *
   * @throws IllegalStateException if no scope is active, i.e. {@code createDocument()} was called
   *     from a FEEL expression other than a result/error expression, which cannot resolve it.
   */
  public static String currentCreateDocumentNonce() {
    NonceHolder holder = CURRENT_NONCE.get();
    if (holder == null) {
      throw new IllegalStateException(
          "createDocument() can only be used inside a result or error expression");
    }
    return holder.get();
  }

  private static final class NonceHolder {
    private String value;

    String get() {
      if (value == null) {
        value = "createDocument:" + UUID.randomUUID();
      }
      return value;
    }
  }

  private static final Map<String, List<JavaFunction>> functions =
      Map.of(
          BpmnErrorFunction.NAME, BpmnErrorFunction.FUNCTIONS,
          JobErrorFunction.NAME, JobErrorFunction.FUNCTIONS,
          IgnoreErrorFunction.NAME, IgnoreErrorFunction.FUNCTIONS,
          BackoffFunction.NAME, BackoffFunction.FUNCTIONS,
          CreateDocumentFunction.NAME, CreateDocumentFunction.FUNCTIONS);

  @Override
  public Optional<JavaFunction> resolveFunction(String functionName) {
    throw new IllegalStateException("Should not be invoked.");
  }

  @Override
  public List<JavaFunction> resolveFunctions(String functionName) {
    return functions.getOrDefault(functionName, Collections.emptyList());
  }

  @Override
  public Collection<String> getFunctionNames() {
    return functions.keySet();
  }
}
