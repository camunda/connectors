package io.camunda.connector.email.outbound.protocols.actions;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

public final class ImapSearchEmails implements ImapAction {

  @TemplateProperty(
      label = "Subject search criteria",
      group = "listEmailImap",
      id = "data.subjectCriteria",
      description = "",
      feel = Property.FeelMode.optional,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.subjectCriteria"))
  private String subjectCriteria;

  @TemplateProperty(
      label = "Body search criteria",
      group = "listEmailImap",
      id = "data.bodyCriteria",
      description = "",
      optional = true,
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.bodyCriteria"))
  private String bodyCriteria;

  @TemplateProperty(
      label = "From search criteria",
      group = "listEmailImap",
      id = "data.fromCriteria",
      description = "",
      optional = true,
      feel = Property.FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "data.imapAction.fromCriteria"))
  private String fromCriteria;
}
