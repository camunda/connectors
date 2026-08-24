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
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer;
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
 * <p>It never sees a value produced by a FEEL evaluation: results are bound by {@link
 * AbstractFeelDeserializer#resultMapper}, which does not register this deserializer. A result may
 * carry any process data, and evaluating such a string would send it to the cluster as a new
 * expression, which would then legitimately reference the secret and resolve it.
 */
public class SecretReferenceDeserializer extends JsonDeserializer<String>
    implements ResolvableDeserializer {

  /**
   * Binds the value first. Whatever would otherwise deserialize a {@code String} keeps doing so —
   * the document module registers its own, which turns a document reference into base64 and runs an
   * intrinsic function — and only the string it produces is inspected for a reference. Replacing
   * that deserializer rather than wrapping it would silently drop both.
   */
  private final JsonDeserializer<?> delegate;

  private static final String REFERENCE_PREFIX = "camunda.secrets.";

  /**
   * Matches {@code camunda.secrets.<name>} where the prefix starts a path of its own: no character
   * that could continue an identifier precedes it, and a name follows, bare or backtick-escaped.
   *
   * <p>A plain substring test would also match {@code =mycamunda.secrets.TOKEN} and {@code
   * =foo.camunda.secrets.TOKEN}, which reference no secret, and send a plain string property to the
   * cluster for evaluation.
   *
   * <p>A quote is excluded for the same reason: {@code ="camunda.secrets.TOKEN"} is a string
   * literal, not a reference, and evaluating it would bind the property as the text without its
   * quotes rather than leaving the value alone. This is textual, so it catches the literal that
   * starts with the prefix rather than every literal containing one — {@code ="see
   * camunda.secrets.TOKEN"} is still sent. Seeing that needs the FEEL parse the engine does, and it
   * resolves no secret either way.
   *
   * <p>Strict about what precedes and permissive about what follows: this is a pre-filter, and the
   * engine, which detects references on the parsed FEEL AST, decides what a reference is. Excluding
   * something the engine would report loses a secret silently; including something it will not
   * report costs one round trip and leaves the value literal. Text this pattern does not match — a
   * form with whitespace around the dots, for instance — binds as it stands.
   */
  private static final Pattern REFERENCE_TOKEN =
      Pattern.compile(
          "(?<![\\p{L}\\p{N}_$.`\"'])" + Pattern.quote(REFERENCE_PREFIX) + "[\\p{L}\\p{N}_$`-]");

  public SecretReferenceDeserializer(JsonDeserializer<?> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void resolve(DeserializationContext context) throws JsonMappingException {
    if (delegate instanceof ResolvableDeserializer resolvable) {
      resolvable.resolve(context);
    }
  }

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    String value = (String) delegate.deserialize(parser, context);
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
    return value != null && value.startsWith("=") && REFERENCE_TOKEN.matcher(value).find();
  }
}
