package io.camunda.connector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class BaseTest {

  protected static final String ACTUAL_QUEUE_URL = "https://sqs.region.amazonaws.com/camunda-test";
  protected static final String ACTUAL_QUEUE_REGION = "ap-region-1";
  protected static final String SECRETS = "secrets.";
  protected static final String ACCESS_KEY = "AWS_ACCESS";
  protected static final String SECRET_KEY = "AWS_SECRET";
  protected static final String QUEUE_URL_KEY = "QUEUE_URL";
  protected static final String QUEUE_REGION_KEY = "QUEUE_REGION";
  protected static final String ACTUAL_ACCESS_KEY = "access_key 123456";
  protected static final String ACTUAL_SECRET_KEY = "secret_key 654321";
  protected static final String SQS_MESSAGE_BODY = "{\"myKey\":\"myVal\"}";
  protected static final String WRONG_MESSAGE_BODY = "its wrong msg";
  protected static final String MSG_ID = "nmsgId";

  protected static final Gson GSON = new GsonBuilder().create();
}
