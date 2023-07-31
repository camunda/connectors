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

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an inbound connector property that needs to be deserialized as FEEL expression.
 *
 * <p>Consider the following properties class:
 *
 * <pre>
 *   record MyInboundConnectorProperties({@literal @}FEEL private String property) {}
 * </pre>
 *
 * Raw connector properties contain a FEEL expression, e.g.:
 *
 * <pre>
 *   { "property": "=\"foo\" + \"bar\"" }
 * </pre>
 *
 * Then the property is deserialized as a FEEL expression when the connector is executed.
 *
 * <pre>
 *   MyInboundConnectorProperties properties = ctx.bindProperties(MyInboundConnectorProperties.class);
 *   properties.property(); // returns "foobar"
 * </pre>
 *
 * See also: {@link io.camunda.connector.api.inbound.InboundConnectorContext#bindProperties(Class)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@JacksonAnnotationsInside
@JsonDeserialize
public @interface FEEL {}
