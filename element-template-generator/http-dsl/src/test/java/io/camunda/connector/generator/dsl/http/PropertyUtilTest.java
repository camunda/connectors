package io.camunda.connector.generator.dsl.http;

import static io.camunda.connector.generator.dsl.http.PropertyUtil.parametersPropertyGroup;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PropertyUtilTest {
  @Test
  void shouldAddCorrectHeader() {
    // given
    HttpOperationProperty property =
        HttpOperationProperty.createHiddenProperty(
            "Content-Type", Target.HEADER, "", true, "multipart/form-data");
    HttpOperation httpOperation =
        new HttpOperation(
            "POST_/mypath",
            "POST_/mypath",
            HttpFeelBuilder.string().part("/hello"),
            HttpMethod.GET,
            HttpFeelBuilder.string().part("body"),
            List.of(property),
            null);

    // when
    PropertyGroup propertyGroup = parametersPropertyGroup(List.of(httpOperation));
    List<Property> properties = propertyGroup.properties();

    // expected
    assertThat(properties).hasSize(5);

    assertThat(properties)
        .anyMatch(
            element ->
                element.getValue().equals("={Content-Type: Content_Type}")
                    && element.getType().equals("Hidden"));

    assertThat(properties)
        .anyMatch(
            element ->
                element.getId().equals("POST_/mypath_header_Content-Type")
                    && element.getValue().equals("multipart/form-data")
                    && element.getType().equals("Hidden"));
  }
}
