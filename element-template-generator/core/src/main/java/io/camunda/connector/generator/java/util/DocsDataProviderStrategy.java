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
