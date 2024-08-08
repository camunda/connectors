/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.textract;

import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.Block;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.textract.caller.AmazonAsyncTextractCaller;
import io.camunda.connector.textract.caller.AmazonS3Caller;
import io.camunda.connector.textract.model.TextractExecutionType;
import io.camunda.connector.textract.model.TextractRequest;
import io.camunda.connector.textract.suppliers.util.AmazonS3ClientUtil;
import io.camunda.connector.textract.suppliers.util.AmazonTextractClientUtil;

import java.util.stream.Collectors;

@OutboundConnector(
    name = "AWS Textract",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-textract:1")
@ElementTemplate(
    id = "io.camunda.connectors.AWSTEXTRACT.v1",
    name = "AWS Textract Outbound Connector",
    description =
        "Automatically extract printed text, handwriting, layout elements, and data from any document",
    inputDataClass = TextractRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Configure input")
    },
    documentationRef =
        "https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/amazon-textract/",
    icon = "icon.svg")
public class TextractConnectorFunction implements OutboundConnectorFunction {


    @Override
    public Object execute(OutboundConnectorContext context) throws Exception {
        final var request = context.bindVariables(TextractRequest.class);
        final var reqData = request.getInput();

        final AmazonS3Caller amazonS3Caller = new AmazonS3Caller(AmazonS3ClientUtil.getAmazonS3Client(request));

        if (reqData.executionType().equals(TextractExecutionType.SYNC)) {
            final AmazonTextract amazonTextractClient = AmazonTextractClientUtil
                    .getSyncTextractClient(request);

            var amazonAsyncTextractCaller = new AmazonAsyncTextractCaller(amazonS3Caller, amazonTextractClient);
            final AnalyzeDocumentResult docResult = amazonAsyncTextractCaller.callTextract(reqData);
            return docResult.getBlocks()
                    .stream()
                    .map(Block::getText)
                    .collect(Collectors.toSet());

        }
        // toDo
        return null;
    }
}
