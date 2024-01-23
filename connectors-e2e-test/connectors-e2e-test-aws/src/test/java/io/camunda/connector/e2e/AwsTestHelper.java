/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;

/**
 * Utility class providing helper methods for setting up and managing AWS Lambda and LocalStack in
 * the context of end-to-end testing.
 */
public class AwsTestHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(AwsTestHelper.class);

  /**
   * Waits for LocalStack to become operational. Checks periodically if LocalStack is healthy.
   * Throws a runtime exception if LocalStack does not become healthy within the timeout period.
   *
   * @param localstack The LocalStack container instance.
   * @throws InterruptedException if the waiting thread is interrupted.
   */
  static void waitForLocalStackToBeHealthy(LocalStackContainer localstack)
      throws InterruptedException {
    long startTime = System.currentTimeMillis();
    long maxDurationMillis = 60000; // 1 minute
    boolean isHealthy = false;

    while (System.currentTimeMillis() - startTime < maxDurationMillis) {
      if (localstack.isCreated() && localstack.isHealthy() && localstack.isRunning()) {
        isHealthy = true;
        break;
      }
      Thread.sleep(500); // Wait for 1 second before checking again
    }

    if (!isHealthy) {
      throw new RuntimeException(
          "LocalStack did not become healthy within the expected time. "
              + "Status: created = "
              + localstack.isCreated()
              + ", healthy = "
              + localstack.isHealthy()
              + ", running = "
              + localstack.isRunning());
    }
  }

  /**
   * Waits for the AWS Lambda client to be initialized. This method periodically checks if the
   * Lambda client is ready by attempting to list functions. If it doesn't initialize within the
   * specified timeout, a runtime exception is thrown.
   *
   * @param lambdaClient The AWS Lambda client.
   * @throws InterruptedException if the waiting thread is interrupted.
   */
  static void waitForLambdaClientInitialization(final AWSLambda lambdaClient)
      throws InterruptedException {
    long startTime = System.currentTimeMillis();
    long maxDurationMillis = 30000; // 30 seconds
    while (System.currentTimeMillis() - startTime < maxDurationMillis) {
      try {
        lambdaClient.listFunctions(); // Check if Lambda client is ready
        return; // If the request succeeds, exit the method
      } catch (Exception e) {
        // Log the exception or handle it as necessary
      }
      Thread.sleep(100); // Wait for 100 milliseconds before retrying
    }
    throw new RuntimeException("Lambda client did not initialize within the expected time.");
  }

  /**
   * Initializes the Lambda function for testing. This includes checking if the function already
   * exists and deleting it if so, then creating a new function using the provided ZIP file path.
   *
   * @param lambdaClient The AWS Lambda client.
   * @param lambdaFunctionZipFilePath The file path to the ZIP file containing the Lambda function
   *     code.
   * @param functionName The name of the Lambda function to be created.
   * @throws IOException if an error occurs during file operations.
   */
  static void initializeLambdaFunction(
      final AWSLambda lambdaClient,
      final String lambdaFunctionZipFilePath,
      final String functionName)
      throws IOException {
    try {
      lambdaClient.getFunction(new GetFunctionRequest().withFunctionName(functionName));
      LOGGER.info("Function already exists, deleting: " + functionName);
      lambdaClient.deleteFunction(new DeleteFunctionRequest().withFunctionName(functionName));
    } catch (ResourceNotFoundException e) {
      LOGGER.info("Function does not exist, no need to delete: " + functionName);
    }

    File zipFile = Paths.get(lambdaFunctionZipFilePath).toFile();
    CreateFunctionRequest functionRequest =
        new CreateFunctionRequest()
            .withFunctionName("myLambdaFunction")
            .withRuntime("python3.9")
            .withRole("arn:aws:iam::000000000000:role/lambda-execute")
            .withHandler("lambda_function.lambda_handler")
            .withCode(
                new FunctionCode()
                    .withZipFile(ByteBuffer.wrap(Files.readAllBytes(zipFile.toPath()))));

    lambdaClient.createFunction(functionRequest);
  }

  /**
   * Waits for the specified Lambda function to be ready for invocation. This method checks the
   * function's state and waits until it becomes 'Active'. A runtime exception is thrown if the
   * function is not ready within the number of attempts.
   *
   * @param lambdaClient The AWS Lambda client.
   * @param functionName The name of the Lambda function to check.
   * @throws InterruptedException if the waiting thread is interrupted.
   */
  static void waitForLambdaFunctionToBeReady(
      final AWSLambda lambdaClient, final String functionName) throws InterruptedException {
    LOGGER.info("Waiting for Lambda function to be ready...");
    int attempts = 30;

    while (attempts > 0) {
      try {
        GetFunctionResult function =
            lambdaClient.getFunction(new GetFunctionRequest().withFunctionName(functionName));
        if (function.getConfiguration().getState().equals("Active")) {
          LOGGER.info("Lambda function is ready for invocation.");
          return;
        }
      } catch (ResourceNotFoundException e) {
        LOGGER.info("Lambda function does not exist yet. Waiting...");
      }
      Thread.sleep(1000); // Wait for 1 second before retrying
      attempts--;
    }
    throw new RuntimeException("Lambda function is not ready after waiting.");
  }

  /**
   * Removes Lambda Docker containers that are not automatically cleaned up. This method is a
   * workaround to ensure that all related Docker containers are stopped and removed.
   */
  static void removeLambdaContainers() {
    try {
      // Assuming 'docker' command is available on the path
      ProcessBuilder builder = new ProcessBuilder();

      // List all containers using the specific Lambda image
      builder.command(
          "docker", "ps", "-a", "-q", "--filter", "ancestor=public.ecr.aws/lambda/python:3.9");
      Process process = builder.start();
      List<String> containerIds =
          new BufferedReader(new InputStreamReader(process.getInputStream())).lines().toList();
      process.waitFor();

      // Stop and remove containers
      for (String containerId : containerIds) {
        LOGGER.info("Stopping and removing container: " + containerId);
        // Stop container
        new ProcessBuilder("docker", "stop", containerId).start().waitFor();
        // Remove container
        new ProcessBuilder("docker", "rm", containerId).start().waitFor();
      }
    } catch (IOException | InterruptedException e) {
      LOGGER.error("Failed to stop and remove Lambda containers", e);
    }
  }

  /**
   * Initializes an AmazonSQS client for interacting with AWS SQS service.
   *
   * @param localstack The LocalStack container instance.
   * @return Initialized AmazonSQS client.
   */
  public static AmazonSQS initSqsClient(LocalStackContainer localstack) {
    return AmazonSQSClientBuilder.standard()
        .withCredentials(
            new AWSStaticCredentialsProvider(
                new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())))
        .withEndpointConfiguration(
            new AwsClientBuilder.EndpointConfiguration(
                localstack.getEndpoint().toString(), localstack.getRegion()))
        .build();
  }

  /**
   * Create an SQS queue with specified attributes.
   *
   * @param sqsClient The AmazonSQS client.
   * @param queueName The name of the queue to be created.
   * @return The URL of the created queue.
   */
  public static String createQueue(AmazonSQS sqsClient, String queueName, boolean isFifo) {
    CreateQueueRequest createQueueRequest = new CreateQueueRequest().withQueueName(queueName);
    if (isFifo) {
      createQueueRequest
          .addAttributesEntry("FifoQueue", "true")
          .addAttributesEntry("ContentBasedDeduplication", "true");
    }
    CreateQueueResult createQueueResult = sqsClient.createQueue(createQueueRequest);
    LOGGER.info("Created SQS queue: {}", queueName);
    return createQueueResult.getQueueUrl().replace("localhost", "127.0.0.1");
  }

  /**
   * Deletes the specified SQS queue.
   *
   * @param sqsClient The AmazonSQS client.
   * @param queueUrl The URL of the queue to be deleted.
   */
  public static void deleteQueue(AmazonSQS sqsClient, String queueUrl) {
    sqsClient.deleteQueue(new DeleteQueueRequest(queueUrl));
    LOGGER.info("Deleted SQS queue: {}", queueUrl);
  }

  /**
   * Polls messages from the specified SQS queue.
   *
   * @param sqsClient The AmazonSQS client.
   * @param queueUrl The URL of the queue to poll messages from.
   * @return List of messages.
   */
  public static List<Message> receiveMessages(AmazonSQS sqsClient, String queueUrl) {
    ReceiveMessageRequest receiveMessageRequest =
        new ReceiveMessageRequest(queueUrl)
            .withMessageAttributeNames("All")
            .withWaitTimeSeconds(5)
            .withMaxNumberOfMessages(1);
    ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);
    List<Message> messages = receiveMessageResult.getMessages();
    LOGGER.info("Received {} messages from SQS queue: {}", messages.size(), queueUrl);
    return messages;
  }
}
