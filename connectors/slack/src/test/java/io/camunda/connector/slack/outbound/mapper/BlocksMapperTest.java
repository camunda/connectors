/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.mapper;

import static io.camunda.connector.slack.outbound.mapper.BlocksMapper.REMOTE_FILE_SOURCE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.model.File;
import com.slack.api.model.block.*;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlocksMapperTest {

  private static final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();

  @Test
  void mapBlocksWithText() {
    String text = " just a text";

    List<LayoutBlock> result = BlocksMapper.mapBlocks(text, null);

    List<LayoutBlock> expected =
        List.of(
            ContextBlock.builder()
                .elements(List.of(PlainTextObject.builder().text(text).build()))
                .build());

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapBlocksWithJsonNode() throws JsonProcessingException {

    JsonNode jsonNode = getBlocksAsJson();

    List<LayoutBlock> result = BlocksMapper.mapBlocks(null, jsonNode);

    List<LayoutBlock> expected = getExpectedBlocks();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapBlocksWithFilesAndJsonNode() throws JsonProcessingException {
    String fileId = "id";
    List<File> files = List.of(File.builder().id(fileId).build());

    List<LayoutBlock> expected = new ArrayList<>(getExpectedBlocks());
    expected.add(FileBlock.builder().fileId(fileId).source(REMOTE_FILE_SOURCE).build());

    List<LayoutBlock> result = BlocksMapper.mapBlocks(files, null, getBlocksAsJson());

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapBlocksWithFilesAndText() throws JsonProcessingException {
    String fileId = "id";
    List<File> files = List.of(File.builder().id(fileId).build());

    String text = "just a text";

    List<LayoutBlock> expected =
        List.of(
            ContextBlock.builder()
                .elements(List.of(PlainTextObject.builder().text(text).build()))
                .build(),
            FileBlock.builder().fileId(fileId).source(REMOTE_FILE_SOURCE).build());

    List<LayoutBlock> result = BlocksMapper.mapBlocks(files, text, null);

    assertThat(result).isEqualTo(expected);
  }

  private JsonNode getBlocksAsJson() throws JsonProcessingException {
    String blocks =
        """
                 [
                	{
                		"type": "header",
                		"text": {
                			"type": "plain_text",
                			"text": "New request"
                		}
                	},
                	{
                		"type": "section",
                		"fields": [
                			{
                				"type": "mrkdwn",
                				"text": "*When:* Aug 10 - Aug 13"
                			}
                		]
                	}
                 ]
                """;

    return objectMapper.readTree(blocks);
  }

  private List<LayoutBlock> getExpectedBlocks() {
    return List.of(
        HeaderBlock.builder().text(PlainTextObject.builder().text("New request").build()).build(),
        SectionBlock.builder()
            .fields(List.of(MarkdownTextObject.builder().text("*When:* Aug 10 - Aug 13").build()))
            .build());
  }
}
