/*
Java code to be run on the AWS EC2 that searches for text in images with cars in them.
*/

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

public class textfinder {

    // Taken from the AWS Docs Java->SQS->SQSExample.java file.
    public static List<Message> receiveMessages(SqsClient sqsClient, String queueUrl) {

        //System.out.println("\nReceive messages");
        // snippet-start:[sqs.java2.sqs_example.retrieve_messages]
        try {
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(5)
                    .build();
            
            return sqsClient.receiveMessage(receiveMessageRequest).messages();

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return null;

    }
    // This function is taken directly from Google Search AI overview.
    public static void deleteSingleMessage(SqsClient sqsClient, String queueUrl, String receiptHandle) {
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();

        sqsClient.deleteMessage(deleteMessageRequest);
        System.out.println("Deleted message with receipt handle: " + receiptHandle);
    }

    // Same source as above.
    public static void deleteMessages(SqsClient sqsClient, String queueUrl, List<Message> messages) {
        //System.out.println("\nDelete Messages");

        try {
            for (Message message : messages) {
                DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build();
                sqsClient.deleteMessage(deleteMessageRequest);
            }
        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

    }

    /**
     * https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/get-started.html
     */

    /**
     * Taken from the AWS Docs Java->Rekognition->DetectText.java page.
     * Detects text labels in an image stored in an S3 bucket using Amazon Rekognition.
     *
     * @param rekClient    an instance of the Amazon Rekognition client
     * @param bucketName   the name of the S3 bucket where the image is stored
     * @param sourceImage  the name of the image file in the S3 bucket
     * @throws RekognitionException if an error occurs while calling the Amazon Rekognition API
     */
    public static String detectTextLabels(RekognitionClient rekClient, String bucketName, String sourceImage) {
        try {
            S3Object s3ObjectTarget = S3Object.builder()
                    .bucket(bucketName)
                    .name(sourceImage)
                    .build();

            Image souImage = Image.builder()
                    .s3Object(s3ObjectTarget)
                    .build();

            DetectTextRequest textRequest = DetectTextRequest.builder()
                    .image(souImage)
                    .build();

            DetectTextResponse textResponse = rekClient.detectText(textRequest);
            List<TextDetection> textCollection = textResponse.textDetections();
            String mytext = "";
            System.out.println("Detected lines and words");
            for (TextDetection text : textCollection) {
                System.out.println("Detected: " + text.detectedText());
                System.out.println("Confidence: " + text.confidence().toString());
                System.out.println("Id : " + text.id());
                System.out.println("Parent Id: " + text.parentId());
                System.out.println("Type: " + text.type());
                System.out.println();
                // textCollection has many texts, some lines some words; lines and words have overlap content
                // parse only lines will get all content and avoid duplication
                if (text.typeAsString().contains("LINE")) {
                    mytext += text.detectedText() + " | ";
                }

            }
            return mytext;

        } catch (RekognitionException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        return("NO_TEXT_FOUND");
    }

    public static void main(String[] args) {
        //https://cs643-njit-project1.s3.dualstack.us-east-1.amazonaws.com
        String bucketName = "cs643-njit-project1";
        Region region = Region.US_EAST_1;
        RekognitionClient rekClient = RekognitionClient.builder()
                .region(region)
                .build();
        String queueName = "ds643-project1-queue.fifo";
        System.out.println("Textfinder online, preparing to read queue msgs\n");
        
        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();
        GetQueueUrlResponse getQueueUrlResponse = sqsClient
                    .getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
        String queueUrl = getQueueUrlResponse.queueUrl();

        try (FileWriter myWriter = new FileWriter("output.txt");) {
            while (true) {
            String image_name = "";
            // Grabbing messages 1 at a time
            List<Message> messages = receiveMessages(sqsClient, queueUrl);
            if (messages.isEmpty()) {
                // waiting for more image names to show up
                System.out.println("Nothing in queue; waiting for more messages\n");
                continue;
            }
            image_name = messages.get(0).body();
            for (Message message : messages) { // clean up by deleting each read msg
                deleteSingleMessage(sqsClient, queueUrl, messages.get(0).receiptHandle());
            }
            if (image_name.contains("-1")) {
                System.out.println("All car images processed for text, outputs written, textfinder closing down\n");
                myWriter.close();
                rekClient.close();
                sqsClient.close();
                return;
            }
            else {
                image_name += ".jpg";
                String label = detectTextLabels(rekClient, bucketName, image_name);
                myWriter.write("Image " + image_name + " has text: " + label + "\n");
                System.out.println("Written data for " + image_name + " into file\n");
                System.out.println("Written the following data: " + label + "\n");
            }
        }
        } catch(IOException e) {
            System.err.println(e);
            System.exit(1);
        }
        //myWriter.write("Filename | Text lines (separated by vertical bars)\n");
        
    }
}

