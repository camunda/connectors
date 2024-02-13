/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.xml;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connectors.soap.xml.ToJsonMapper.AttributeMode;

public class Mapper {
  public static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();
  public static final String DEFAULT_CONTENT_NAME = "$content";
  public static final String DEFAULT_ATTRIBUTE_PREFIX = "@";
  public static final boolean DEFAULT_PRESERVE_NAMESPACES = false;
  public static final AttributeMode DEFAULT_ATTRIBUTE_MODE = AttributeMode.noPrefix;
  public static final boolean DEFAULT_OMIT_XML_DECLARATION = false;
  public static final boolean DEFAULT_XML_PRETTY_PRINT = true;

  private Mapper() {}

  public static ToJsonMapperBuilder toJson() {
    return new ToJsonMapperBuilder();
  }

  public static ToXmlMapperBuilder toXml() {
    return new ToXmlMapperBuilder();
  }

  public abstract static sealed class MapperBuilder<T, SELF> {
    protected ObjectMapper objectMapper = DEFAULT_OBJECT_MAPPER;
    protected String contentName = DEFAULT_CONTENT_NAME;
    protected String attributePrefix = DEFAULT_ATTRIBUTE_PREFIX;

    protected abstract SELF self();

    public SELF withObjectMapper(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
      return self();
    }

    public SELF withContentName(String contentName) {
      this.contentName = contentName;
      return self();
    }

    public SELF withAttributePrefix(String attributePrefix) {
      this.attributePrefix = attributePrefix;
      return self();
    }

    public abstract T build();
  }

  public static final class ToXmlMapperBuilder
      extends MapperBuilder<ToXmlMapper, ToXmlMapperBuilder> {
    private boolean omitXmlDeclaration = DEFAULT_OMIT_XML_DECLARATION;
    private boolean prettyPrint = DEFAULT_XML_PRETTY_PRINT;

    private ToXmlMapperBuilder() {}

    @Override
    protected ToXmlMapperBuilder self() {
      return this;
    }

    @Override
    public ToXmlMapper build() {
      return new ToXmlMapper(
          objectMapper, contentName, attributePrefix, prettyPrint, omitXmlDeclaration);
    }

    public ToXmlMapperBuilder withPrettyPrint(boolean prettyPrint) {
      this.prettyPrint = prettyPrint;
      return this;
    }

    public ToXmlMapperBuilder withOmitXmlDeclaration(boolean omitXmlDeclaration) {
      this.omitXmlDeclaration = omitXmlDeclaration;
      return this;
    }
  }

  public static final class ToJsonMapperBuilder
      extends MapperBuilder<ToJsonMapper, ToJsonMapperBuilder> {
    private boolean preserveNamespaces = DEFAULT_PRESERVE_NAMESPACES;
    private AttributeMode attributeMode = DEFAULT_ATTRIBUTE_MODE;

    private ToJsonMapperBuilder() {}

    public ToJsonMapperBuilder withPreserveNamespaces(boolean preserveNamespaces) {
      this.preserveNamespaces = preserveNamespaces;
      return this;
    }

    public ToJsonMapperBuilder withAttributeMode(AttributeMode attributeMode) {
      this.attributeMode = attributeMode;
      return this;
    }

    @Override
    protected ToJsonMapperBuilder self() {
      return this;
    }

    @Override
    public ToJsonMapper build() {
      return new ToJsonMapper(
          objectMapper, preserveNamespaces, attributeMode, contentName, attributePrefix);
    }
  }
}
