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
package io.camunda.connector.feel.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Resolves {@code camunda.secrets.<name>} references in string values that FEEL would otherwise
 * never evaluate.
 *
 * <p>Only a fraction of connector credential fields carry {@code @FEEL}; the rest are bound as
 * plain strings, and a secret reference written in one of those has to work too. Registering this
 * for {@code String} covers them, and covers the string values inside {@code Map} and {@code List}
 * properties and values reached through a field declared as {@code Object} — an {@code
 * Authorization} header is exactly where a secret goes.
 *
 * <p>It is deliberately narrow in two ways. A value is evaluated only when it is written as an
 * expression and names a secret reference; everything else is bound exactly as {@link
 * StringDeserializer} binds it. And it evaluates only when the reader carries an evaluator of its
 * own, which is how the runtime hands each connector context its cluster-backed, secret-resolving
 * one. Without that evaluator there is nothing useful to do, and evaluating anyway would consume
 * the reference: the same mapper is used to round-trip raw properties through legacy secret
 * replacement, where the value must survive untouched.
 *
 * <p>A {@code @FEEL}-annotated property keeps its own deserializer, because a property-level
 * deserializer wins over a type-registered one.
 *
 * <p>No FEEL context is supplied. The point is to resolve a secret reference, not to turn plain
 * properties into expression fields, so an expression naming process data belongs on a
 * {@code @FEEL} property as before.
 *
 * <p>It never acts on a value that came out of a FEEL evaluation. Converting an evaluation result
 * runs it back through the deserializers, and a result may carry anything the process holds — a
 * webhook payload, a correlated variable. Treating such a string as expression source would send it
 * to the cluster as a new expression, which would then legitimately reference the secret and
 * resolve it, laundering data the cluster reported no reference for into a resolved value.
 */
public class SecretReferenceDeserializer extends StringDeserializer {

  private static final String REFERENCE_PREFIX = "camunda.secrets.";

  /**
   * Decides, from the text alone, whether a value is worth sending to the cluster as an expression.
   *
   * <p>A bare {@code contains} is not that decision: {@code =mycamunda.secrets.TOKEN} and {@code
   * =foo.camunda.secrets.TOKEN} name something else entirely — the cluster reports no secret for
   * either — yet they carry the prefix as a substring, so a plain string property holding one was
   * sent for evaluation. That turns plain properties into expression fields, which is exactly what
   * this deserializer is meant not to do. The prefix has to start a path of its own, so nothing
   * that could continue an identifier may precede it, and a name has to follow — bare, or
   * backtick-escaped as a dashed name must be.
   *
   * <p>Strict about what precedes and permissive about what follows, deliberately. This is a
   * pre-filter, not the decision: the engine detects references on the parsed FEEL AST, so it, not
   * this pattern, says what a reference is. Filtering out something the engine would have reported
   * loses a secret silently; letting through something it will not report costs one round trip and
   * a value that stays literal. Text this pattern cannot see a reference in — a form carrying
   * whitespace around the dots, say — binds as it stands, which fails closed and visibly.
   */
  private static final Pattern REFERENCE_TOKEN =
      Pattern.compile(
          "(?<![\\p{L}\\p{N}_$.`])" + Pattern.quote(REFERENCE_PREFIX) + "[\\p{L}\\p{N}_$`-]");

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    String value = super.deserialize(parser, context);
    if (!namesSecretReference(value) || AbstractFeelDeserializer.isEvaluationResult(context)) {
      return value;
    }
    FeelExpressionEvaluator evaluator = AbstractFeelDeserializer.resolveEvaluator(context, null);
    if (evaluator == null) {
      return value;
    }
    // A cluster that does not preserve the reference through evaluation answers with nothing. The
    // reference has to stay visible in that case, rather than binding the property as empty.
    String evaluated = evaluator.evaluate(value, String.class);
    return evaluated == null ? value : evaluated;
  }

  private static boolean namesSecretReference(String value) {
    return value != null && value.startsWith("=") && REFERENCE_TOKEN.matcher(value).find();
  }
}
