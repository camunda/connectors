/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.util;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.mime.MimeTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FileUtilTest {

  @ParameterizedTest
  @CsvSource({"test-file, txt", "test.file, pdf"})
  void defineNameAndType(String fileName, String fileExtension) {
    String fullFileName = fileName + "." + fileExtension;

    Pair<String, String> result = FileUtil.defineNameAndType(fullFileName);

    assertThat(result.getLeft()).isEqualTo(fileName);
    assertThat(result.getRight()).isEqualTo(fileExtension);
  }

  @Test
  void defineNameAndTypeWhenFileWithoutExtensionShouldReturnOnlyName() {
    String fileName = "test-file";

    Pair<String, String> result = FileUtil.defineNameAndType(fileName);

    assertThat(result.getLeft()).isEqualTo(fileName);
    assertThat(result.getRight()).isEmpty();
  }

  @Test
  void defineType() throws MimeTypeException {
    String result = FileUtil.defineType("text/plain");
    assertThat(result).isEqualTo("txt");
  }
}
