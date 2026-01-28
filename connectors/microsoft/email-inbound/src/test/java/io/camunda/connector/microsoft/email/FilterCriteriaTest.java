/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.microsoft.email.model.config.FilterCriteria;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilterCriteriaTest {

  @Nested
  class SimpleConfigurationFilterStrings {

    @Test
    void getFilterString_onlyUnreadTrue_returnsCorrectFilter() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(true, null, null);

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEqualTo("isRead eq false");
    }

    @Test
    void getFilterString_onlyUnreadFalse_returnsEmptyString() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(false, null, null);

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    void getFilterString_subjectContains_returnsCorrectFilter() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(false, "Invoice", null);

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEqualTo("contains(subject, 'Invoice')");
    }

    @Test
    void getFilterString_subjectContainsNull_ignoresSubjectFilter() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(false, null, null);

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    void getFilterString_subjectContainsBlank_ignoresSubjectFilter() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(false, "   ", null);

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    void getFilterString_onlyUnreadAndSubjectContains_combinesWithAnd() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(true, "Order", null);

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEqualTo("isRead eq false and contains(subject, 'Order')");
    }

    @Test
    void getFilterString_subjectContainsWithSpecialCharacters_includesCharactersInFilter() {
      // Given
      var config =
          new FilterCriteria.SimpleConfiguration(false, "Re: Meeting @2pm & discussion", null);

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEqualTo("contains(subject, 'Re: Meeting @2pm & discussion')");
    }

    @Test
    void getFilterString_fromAddress_returnsCorrectFilter() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(false, null, "invoice@vendor.com");

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEqualTo("from/emailAddress/address eq 'invoice@vendor.com'");
    }

    @Test
    void getFilterString_fromAddressNull_ignoresFromFilter() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(false, null, null);

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    void getFilterString_fromAddressBlank_ignoresFromFilter() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(false, null, "   ");

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    void getFilterString_allThreeFilters_combinesWithAnd() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(true, "Invoice", "billing@company.com");

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result)
          .isEqualTo(
              "isRead eq false and contains(subject, 'Invoice') and from/emailAddress/address eq 'billing@company.com'");
    }

    @Test
    void getFilterString_onlyUnreadAndFromAddress_combinesWithAnd() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(true, null, "sender@example.com");

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result)
          .isEqualTo("isRead eq false and from/emailAddress/address eq 'sender@example.com'");
    }

    @Test
    void getFilterString_subjectAndFromAddress_combinesWithAnd() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(false, "Order", "sales@company.com");

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result)
          .isEqualTo(
              "contains(subject, 'Order') and from/emailAddress/address eq 'sales@company.com'");
    }

    @Test
    void getFilterString_subjectContainsWithSingleQuote_escapesSingleQuote() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(false, "John's Invoice", null);

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEqualTo("contains(subject, 'John''s Invoice')");
    }

    @Test
    void getFilterString_subjectContainsWithMultipleSingleQuotes_escapesAllSingleQuotes() {
      // Given
      var config =
          new FilterCriteria.SimpleConfiguration(false, "It's Maria's 'urgent' request", null);

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEqualTo("contains(subject, 'It''s Maria''s ''urgent'' request')");
    }

    @Test
    void getFilterString_fromAddressWithSingleQuote_escapesSingleQuote() {
      // Given
      var config = new FilterCriteria.SimpleConfiguration(false, null, "o'brien@example.com");

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result).isEqualTo("from/emailAddress/address eq 'o''brien@example.com'");
    }

    @Test
    void getFilterString_bothFieldsWithSingleQuotes_escapesBothFields() {
      // Given
      var config =
          new FilterCriteria.SimpleConfiguration(true, "Bob's order", "o'neil@company.com");

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result)
          .isEqualTo(
              "isRead eq false and contains(subject, 'Bob''s order') and from/emailAddress/address eq 'o''neil@company.com'");
    }
  }

  @Nested
  class AdvancedConfigurationFilterStrings {

    @Test
    void getFilterString_returnsProvidedFilterString() {
      // Given
      var config =
          new FilterCriteria.AdvancedConfiguration(
              "isRead eq false and from/emailAddress/address eq 'sender@example.com'");

      // When
      String result = config.getFilterString();

      // Then
      assertThat(result)
          .isEqualTo("isRead eq false and from/emailAddress/address eq 'sender@example.com'");
    }
  }
}
