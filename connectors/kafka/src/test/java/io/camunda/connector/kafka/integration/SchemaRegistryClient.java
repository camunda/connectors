/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.integration;

import java.io.IOException;
import java.util.List;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaRegistryClient {
  private static final Logger LOG = LoggerFactory.getLogger(SchemaRegistryClient.class);

  public List<String> registerAll(
      List<SchemaWithTopic> schemaWithTopicList, String schemaRegistryHost) {
    return schemaWithTopicList.stream()
        .map(
            schemaWithTopic ->
                register(schemaWithTopic.schema(), schemaRegistryHost, schemaWithTopic.topic()))
        .toList();
  }

  public String register(String schemaJson, String schemaRegistryHost, String topic) {
    String schemaRegistryUrl =
        "http://" + schemaRegistryHost + "/subjects/" + topic + "-value/versions";

    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost request = new HttpPost(schemaRegistryUrl);
      request.setHeader("Content-Type", "application/vnd.schemaregistry.v1+json");

      String schemaType = schemaJson.contains("record") ? "AVRO" : "JSON";
      StringEntity entity =
          new StringEntity(
              "{\"schemaType\":\""
                  + schemaType
                  + "\", \"schema\": \""
                  + StringEscapeUtils.escapeJson(schemaJson)
                  + "\"}");
      request.setEntity(entity);

      try (CloseableHttpResponse response = httpClient.execute(request)) {
        String responseBody = EntityUtils.toString(response.getEntity());
        LOG.info("Response: {}", responseBody);

        if (response.getCode() == 200) {
          LOG.info("Schema successfully published");
          return responseBody;
        } else {
          LOG.error("Failed to publish schema: {}", responseBody);
          return null;
        }
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String getSubjects(String schemaRegistryHost) {
    String schemaRegistryUrl = "http://" + schemaRegistryHost + "/subjects";

    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpGet request = new HttpGet(schemaRegistryUrl);
      request.setHeader("Content-Type", "application/vnd.schemaregistry.v1+json");

      try (CloseableHttpResponse response = httpClient.execute(request)) {
        String responseBody = EntityUtils.toString(response.getEntity());
        LOG.info("Response: {}", responseBody);

        if (response.getCode() == 200) {
          LOG.info("Schema successfully published");
          return responseBody;
        } else {
          LOG.error("Failed to publish schema: {}", responseBody);
          return null;
        }
      } catch (ParseException e) {
        throw new RuntimeException(e);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  record SchemaWithTopic(String schema, String topic) {}
}
