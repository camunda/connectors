/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record of the agentic AI modules: Jackson leaves out null values and ignores unknown
 * properties, and record-builder generates a builder with immutable collections.
 *
 * <p>The builder copies each map and set with {@code Map.copyOf} and {@code Set.copyOf}. Their
 * iteration order changes with every JVM start. A hash, an id or any value compared across runs
 * must not depend on it: sort the map keys where the value is computed, carry a meaningful order as
 * a list, and put no set into a hashed value, because no mapper flag sorts a set. Do not work
 * around the copy with another annotation or collection type.
 */
@JacksonAnnotationsInside
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@RecordBuilder.Template(
    options =
        @RecordBuilder.Options(
            publicBuilderConstructors = true,
            interpretNotNulls = true,
            defaultNotNull = true,
            useImmutableCollections = true))
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface AgenticAiRecord {}
