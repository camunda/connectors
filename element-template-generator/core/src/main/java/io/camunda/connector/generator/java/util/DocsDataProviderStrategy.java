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
package io.camunda.connector.generator.java.util;

import java.lang.annotation.Annotation;
import uk.co.jemos.podam.api.AbstractRandomDataProviderStrategy;
import uk.co.jemos.podam.api.AttributeMetadata;
import uk.co.jemos.podam.api.RandomDataProviderStrategy;
import uk.co.jemos.podam.common.AttributeStrategy;
import uk.co.jemos.podam.common.ManufacturingContext;

public class DocsDataProviderStrategy extends AbstractRandomDataProviderStrategy {

  @Override
  public RandomDataProviderStrategy addOrReplaceAttributeStrategy(
      Class<? extends Annotation> annotationClass, AttributeStrategy<?> attributeStrategy) {
    return super.addOrReplaceAttributeStrategy(annotationClass, attributeStrategy);
  }

  @Override
  public <T> T getTypeValue(
      AttributeMetadata attributeMetadata,
      ManufacturingContext manufacturingCtx,
      Class<T> pojoType) {
    if (attributeMetadata.getAttributeType() == String.class) {
      return (T) "string";
    } else {
      return super.getTypeValue(attributeMetadata, manufacturingCtx, pojoType);
    }
  }
}
