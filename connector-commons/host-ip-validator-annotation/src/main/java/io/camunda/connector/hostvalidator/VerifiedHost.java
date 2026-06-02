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
package io.camunda.connector.hostvalidator;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marker constraint annotation: the string value must be a hostname whose resolved IP address(es)
 * pass the configuration of the VerifiedHostValidator instance applied by the validator factory.
 *
 * <p>This annotation intentionally carries no configuration — the allow/deny ranges and other
 * options live on the stateful VerifiedHostValidator that the {@link
 * jakarta.validation.ConstraintValidatorFactory} returns. Register a Spring bean (or otherwise wire
 * up a constraint validator factory) holding the desired VerifiedHostValidator.Config.
 *
 * <h2>Security caveat — TOCTOU / DNS rebinding</h2>
 *
 * <p><strong>Validation is not a substitute for connection-time enforcement.</strong> This
 * annotation runs at Bean Validation time — typically when a connector job is picked up — but the
 * outbound HTTP/SMTP/IMAP/POP3 connection that uses the host happens later, after a separate DNS
 * lookup. An attacker who controls the authoritative DNS for an allowed hostname can serve a
 * short-TTL record that resolves to a <em>public</em> IP for the validation lookup and to a
 * <em>private</em> IP (e.g. {@code 169.254.169.254}, RFC 1918) for the connection lookup. This is
 * the classic DNS-rebinding / time-of-check-to-time-of-use (TOCTOU) gap and is inherent to any
 * annotation-based host check.
 *
 * <p>True defense requires re-validating the resolved IP at <em>connection</em> time — for example,
 * via a custom {@code DnsResolver} / {@code SocketFactory} (Apache HttpClient), a custom {@code
 * Address} resolver (OkHttp), or by resolving the host once and dialing the literal IP (pinning) so
 * the connection uses the same address that was checked. Consider this annotation a first line of
 * defense against obvious mistakes (e.g. a user pasting an RFC 1918 URL), not a complete SSRF
 * mitigation.
 */
@Documented
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT, TYPE_USE})
@Constraint(validatedBy = {}) // Configured via XML in the validator
public @interface VerifiedHost {

  String message() default "Host is not allowed";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  boolean isUrl() default false;
}
