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

package io.camunda.connector.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the path to a secret field as well as the secret itself. Secrets are always of type {@link
 * String} or a container type like Array, Map, List, Iterable, or a custom class. For
 * container-type fields, a container <code>handler</code> can be defined. Otherwise, the SDK's
 * default {@link SecretHandler} is used.
 *
 * <p><b>Deprecated:</b> Secret support is now available all fields during the binding process
 * without the need to annotate individual fields.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Deprecated(forRemoval = true)
public @interface Secret {

  @Deprecated
  Class<?> handler() default Object.class;
}
