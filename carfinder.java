/*
Java code to be run on the AWS EC2 that decides if images from the S3 bucket has a car in it.
*/

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

//import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.time.Instant;


public class carfinder {

	static String not_a_car = "NONE";
	public static void sendMessage(SqsClient sqsClient, String queueName, String message) {
		try {
			// I already have a queue, no need to make it again... right?
			// CreateQueueRequest request = CreateQueueRequest.builder()
			// 		.queueName(queueName)
			// 		.build();
			// sqsClient.createQueue(request);

			GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
					.queueName(queueName)
					.build();

			// SQS FIFO queues block all messages with the same dedup ID if another msg w/ same ID appears within 5 minutes
			// I can't be bothered to rebuild the queue so I am brute force bypassing this using some timestamps
			long epochSeconds = Instant.now().getEpochSecond();
			epochSeconds *= 100;
			epochSeconds += Integer.parseInt(message);
			String queueUrl = sqsClient.getQueueUrl(getQueueRequest).queueUrl();
			SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
					.queueUrl(queueUrl)
					.messageBody(message)
					.messageDeduplicationId(Long.toString(epochSeconds))
					.messageGroupId("carfinder_images") // Required for FIFO queues
					//.delaySeconds(5)
					.build();

			sqsClient.sendMessage(sendMsgRequest);

		} catch (SqsException e) {
			System.err.println(e.awsErrorDetails().errorMessage());
			System.exit(1);
		}
	}

	/**
	 * https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/get-started.html
	 */

	/**
	 * Taken from the AWS Docs Java->Rekognition->DetectLabels.java page.
	 * I am not downloading the images to the ec2 because this function apparently lets rek grab
	 * the image direct from s3 given a bucket name and an image name.
	 * Detects the labels in an image stored in an Amazon S3 bucket using the Amazon Rekognition service.
	 *
	 * @param rekClient     the Amazon Rekognition client used to make the detection request
	 * @param bucketName    the name of the Amazon S3 bucket where the image is stored
	 * @param sourceImage   the name of the image file to be analyzed
	 */
	public static String detectImageLabels(RekognitionClient rekClient, String bucketName, String sourceImage) {
		try {
			S3Object s3ObjectTarget = S3Object.builder()
					.bucket(bucketName)
					.name(sourceImage)
					.build();

			Image souImage = Image.builder()
					.s3Object(s3ObjectTarget)
					.build();

			DetectLabelsRequest detectLabelsRequest = DetectLabelsRequest.builder()
					.image(souImage)
					.maxLabels(10)
					.build();

			DetectLabelsResponse labelsResponse = rekClient.detectLabels(detectLabelsRequest);
			List<Label> labels = labelsResponse.labels();
			System.out.println("Detected labels for the given photo");
			
			for (Label label : labels) {
				System.out.println(label.name() + ": " + label.confidence().toString());
				if (label.name().contains("Car") && label.confidence() > 80.0) {
					System.out.println("!!!!! Found a car !!!!!\n");
					return(label.name());
				}
			}
			return(not_a_car);

		} catch (RekognitionException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}
		return(not_a_car);
	}

	// From the AWS Docs Java->S3->S3Actions.java file.
	// public Long downloadFile(S3TransferManager transferManager, String bucketName,
	// 						String key, String downloadedFileWithPath) {
	// 	DownloadFileRequest downloadFileRequest = DownloadFileRequest.builder()
	// 			.getObjectRequest(b -> b.bucket(bucketName).key(key))
	// 			.destination(Paths.get(downloadedFileWithPath))
	// 			.build();

	// 	FileDownload downloadFile = transferManager.downloadFile(downloadFileRequest);

	// 	CompletedFileDownload downloadResult = downloadFile.completionFuture().join();
	// 	logger.info("Content length [{}]", downloadResult.response().contentLength());
	// 	return downloadResult.response().contentLength();
	// }

	public static void main(String[] args) {
		//https://cs643-njit-project1.s3.dualstack.us-east-1.amazonaws.com
		String bucketName = "cs643-njit-project1";
		//S3TransferManager transferManager = S3TransferManager.create();
		
		//String sourceImage = "1.jpg",
		//String download_img_path = "local.jpg";
		Region region = Region.US_EAST_1;
		RekognitionClient rekClient = RekognitionClient.builder()
				.region(region)
				.build();

		String queueName = "ds643-project1-queue.fifo";
		SqsClient sqsClient = SqsClient.builder()
				.region(Region.US_EAST_1)
				.build();

		// Here I know beforehand that the images have names from 1.jpg to 10.jpg
		// Optimally I should have queried the S3 bucket to get a list of item names to use for iteration.
		System.out.println("Carfinder active, sending images to rek for classification\n");
		for (int i = 1; i <= 10; i++) {
			String sourceImage = String.format("%s.jpg", i);
			String label = detectImageLabels(rekClient, bucketName, sourceImage);
			if (label != not_a_car) {
				sendMessage(sqsClient, queueName, Integer.toString(i));
				System.out.println("Sent image " + sourceImage + " to SQS\n");
			}
		}
		//downloadFile(transferManager, bucketName, sourceImage, download_img_path);
		// send -1 to SQS here to signify no more imgs
		sendMessage(sqsClient, queueName, Integer.toString(-1));
		rekClient.close();
		sqsClient.close();
		System.out.println("All images handled, carfinder closing down\n");
	}
}

