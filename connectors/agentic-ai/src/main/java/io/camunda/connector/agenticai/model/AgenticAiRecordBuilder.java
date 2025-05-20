/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@RecordBuilder.Template(
    options =
        @RecordBuilder.Options(
            addFunctionalMethodsToWith = true,
            interpretNotNulls = true,
            defaultNotNull = true,
            useImmutableCollections = true,
            addSingleItemCollectionBuilders = true))
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Inherited
public @interface AgenticAiRecordBuilder {}
