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
package io.camunda.connector.runtime.core.secret;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.runtime.core.secret.ClusterVariableReference.Scope;
import org.junit.jupiter.api.Test;

class ClusterVariableReferenceTest {

  @Test
  void findsEachScope() {
    assertThat(ClusterVariableReference.findAll("=camunda.vars.env.A"))
        .containsExactly(new ClusterVariableReference(Scope.ENV, "A"));
    assertThat(ClusterVariableReference.findAll("=camunda.vars.tenant.B"))
        .containsExactly(new ClusterVariableReference(Scope.TENANT, "B"));
    assertThat(ClusterVariableReference.findAll("=camunda.vars.cluster.C"))
        .containsExactly(new ClusterVariableReference(Scope.CLUSTER, "C"));
  }

  @Test
  void ignoresScopesThatAreNotClusterVariables() {
    // camunda.vars.processInstance.* is a different namespace and holds no cluster variable, so
    // reading a variable by that name would be wrong.
    assertThat(ClusterVariableReference.findAll("=camunda.vars.processInstance.key")).isEmpty();
    assertThat(ClusterVariableReference.findAll("=camunda.vars.somethingElse.X")).isEmpty();
  }

  @Test
  void ignoresAReferenceWithNoVariableName() {
    assertThat(ClusterVariableReference.findAll("=camunda.vars.env")).isEmpty();
    assertThat(ClusterVariableReference.findAll("=camunda.vars.env.")).isEmpty();
  }

  @Test
  void stopsAtTheVariableNameSoATrailingPathStillNamesTheSameVariable() {
    // camunda.vars.env.myVar.a.b reads myVar; which field of it is used is the cluster's business.
    assertThat(ClusterVariableReference.findAll("=camunda.vars.env.myVar.a.b"))
        .containsExactly(new ClusterVariableReference(Scope.ENV, "myVar"));
  }

  @Test
  void findsSeveralReferencesInOneValueAndDeduplicates() {
    var references =
        ClusterVariableReference.findAll(
            "=\"x\" + camunda.vars.env.A + camunda.vars.cluster.B + camunda.vars.env.A");

    assertThat(references)
        .containsExactly(
            new ClusterVariableReference(Scope.ENV, "A"),
            new ClusterVariableReference(Scope.CLUSTER, "B"));
  }

  @Test
  void toleratesNullAndFindsNothingInPlainText() {
    assertThat(ClusterVariableReference.findAll(null)).isEmpty();
    assertThat(ClusterVariableReference.findAll("just a value")).isEmpty();
  }
}
