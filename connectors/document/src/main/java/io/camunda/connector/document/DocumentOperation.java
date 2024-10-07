/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.document;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.document.Document;

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "operation", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = DocumentOperation.GetDocument.class, name = "getDocument"),
  @JsonSubTypes.Type(value = DocumentOperation.CreateDocument.class, name = "createDocument")
})
@TemplateDiscriminatorProperty(name = "operation", label = "Operation")
sealed interface DocumentOperation {

  @TemplateSubType(id = "getDocument", label = "Get Document")
  record GetDocument(
      @TemplateProperty(type = PropertyType.String, feel = FeelMode.required) Document document)
      implements DocumentOperation {}

  @TemplateSubType(id = "createDocument", label = "Create Document")
  record CreateDocument(String content) implements DocumentOperation {}
}
