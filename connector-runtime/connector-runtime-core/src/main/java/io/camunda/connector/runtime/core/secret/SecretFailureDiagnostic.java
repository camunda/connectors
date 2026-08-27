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

/**
 * A secret lookup failure that can say why in text safe to show an operator.
 *
 * <p>Most failures cannot. A secret provider or its client may put anything in a message — a
 * response body from the secret store, a credential echoed back by a rejecting API — so where a
 * failure is reported without the values needed to redact it, the message has to be withheld. That
 * withholding is what this interface makes an exception to.
 *
 * <p>Implement it only where the runtime authors the whole message itself, and keep it that way: a
 * provider's or client's own text must never be interpolated into what {@link
 * #publishableMessage()} returns. A secret's <em>name</em> is fine — it is written in the model
 * that a reader of the incident can already see — but a secret's value, or any part of a store's
 * response, is not.
 *
 * <p>The point is that the two failures this runtime introduces are exactly the ones an operator
 * has to act on, and neither is diagnosable from a type alone: legacy resolution being switched off
 * is fixed by naming the setting and the form that replaced it, and a name that has no reference
 * form is fixed by naming the charset that admits one.
 */
public interface SecretFailureDiagnostic {

  /**
   * Why the lookup failed, in text the runtime wrote and may publish verbatim — to an incident
   * message, or to process variables.
   */
  String publishableMessage();
}
