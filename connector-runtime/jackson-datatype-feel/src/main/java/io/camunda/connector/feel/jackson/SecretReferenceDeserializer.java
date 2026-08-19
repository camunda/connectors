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
 */
public class SecretReferenceDeserializer extends StringDeserializer {

  private static final String REFERENCE_PREFIX = "camunda.secrets.";

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    String value = super.deserialize(parser, context);
    if (!namesSecretReference(value)) {
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
    return value != null && value.startsWith("=") && value.contains(REFERENCE_PREFIX);
  }
}
