# DS 643 AWS Project

Student: Ze Hong Wu

This README includes descriptions and setup instructions for the DS 643 AWS project. See the end of the README for a link to an unlisted YouTube video showing the project compilation and test run steps.

## Purpose

Project description

## Assumptions

The instructions below assumes the following:
1: You are setting up the project from an AWS Academy lab instance. If you are setting up the project as an independent AWS user, you may wish to consult this link: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_temp_use-resources.html
2: You have access to the provided carfinder.java, textfinder.java, and pom.xml files.

## Setup Instructions

0: Navigate to and turn on the AWS Academy lab. Use the "AWS" link in the upper left to navigate to the AWS website.
1: Navigate to the EC2->Instances dashboard. Create a pair of EC2 instances. Use the following settings:
- Name: use whatever you want; I named my EC2s "carfinder" and "textfinder" for easy recognition.
- Quick start AMI: Amazon Linux
- Instance type: t3-micro should be sufficient
- Key pair: select vockey from the drop down menu; this is the SSH key the AWS Academy lab provides for you.
- Network Settings -> Security Groups: Select existing security group
  - Select launch-wizard-1 and launch-wizard-2 from the dropdown menu. 
2: Navigate to the SQS dashboard. Create a FIFO queue. Use the following settings:
- Name: ds643-project1-queue.fifo
  - You can use a different name, but you must update carfinder and textfinder to reflect the new queue name.
- Various timeouts in the Configuration section: use the defaults.
- FIFO queue settings: Content Deduplication -> Off, High Throughput -> On
- Do not change any other settings.

3: Open the EC2->Network and Security->Security Groups dashboard (see the left side menu on the EC2 page) on a new page. There should be three security groups listed on the menu. For launch-wizard-1 and launch-wizard-2, modify their inbound rules so that they accept HTTP, HTTPS, and SSH connections from your IP only.
4: From the AWS lab page, download the PEM file (this will allow you to setup SSH connections to the EC2 instances) to your local device. Do this by clicking on the "AWS Details" button on top of the lab window, then clicking the "Download PEM" button. Move the PEM file to a folder of your choice. The file should have the name `labsuser.pem`.
5: Set chmod for the downloaded pem file to 400 using command line instructions. If your device uses Windows 10/11, use icacls to accomplish this. This is necessary because EC2 instances do not accept ssh connections using PEM files with insufficiently strict access rules.
6: For each EC2, identify its public DNS address on its instance summary page (go to the EC2->Instances dashboard and click on an EC2 instance's link). It should look like `ec2-123-456-789-012.compute-1.amazonaws.com`, but with different numbers. Use the command `ssh -i labsuser.pem ec2-user@your-ec2-dns-address` to remotely log into the EC2 instance.
7: In each EC2 instance, run the following commands: `sudo yum install java`, `sudo yum install maven`.
8: From the AWS lab page, in the AWS Details page, click the "AWS CLI" button. Copy the access key, secret access key, and session token. In each EC2 instance, create a file with the path `~/.aws/credentials`. Use vim to edit the file; paste the copied content into it and save. If `.aws` does not exist yet, create it.
9: For each EC2 instance, create a file with the path `~/.aws/config`. Write into the file the following:
```
[default]
region=us-east-1
```
- Replace us-east-1 with your preferred AWS region.

10: [OPTIONAL] Create a folder in each EC2 instance's `~` directory. All project code will go into this folder.
11: Create a Maven project framework in each EC2 instance using this command:
```
mvn -B archetype:generate \
 -DarchetypeGroupId=software.amazon.awssdk \
 -DarchetypeArtifactId=archetype-lambda -Dservice=s3 -Dregion=US_EAST_1 \
 -DarchetypeVersion=2.42.18 \
 -DgroupId=com.zw4.ds642_project1 \
 -DartifactId=ds642_project1
```
- The values `-DgroupId` and `-DartifactId` can be replaced with whatever you want. Avoid using dashes in them as this may cause Java compile-time errors related to Java package names not being compatible with dashes.
- The value `-DarchetypeVersion` should be whatever the latest version of Maven is. You can Google that information.
- This should create a folder named ds642_project1 (or whatever alternate name you used). The full Maven project framework is located within this folder.

12: Identify some method of moving files from local to remote computers. I personally used sftp; the command to accessing the EC2 instances remotely is `sftp -i labsuser.pem ec2-user@your-ec2-dns-address`. You may consider using WinSCP or any other method.
- Note that you will need to open new cmd windows to run sftp, as a single cmd window cannot both sftp and ssh at the same time.

13: Using the method you chose in Step 12, move the pom.xml file in this repository into the project framework folder. The command to move a file from local to remote is `put local/path/to/pom.xml`. This pom.xml differs from the default generated pom.xml in that it contains dependencies required for running the two Java files.
14: Move carfinder.java and textfinder.java to one EC2 instance each. Use sftp to move them to the following file location: `your_project_folder_name/src/main/java/com/zw4/ds642_project1`.
- Note that the name of the folders after `java/` is dependent on the groupId you set in Step 11; each phrase separated by a period is a nested folder.
  - For example, if your groupId is `test.123.project`, the folder directory should look like `java/test/123/project`.

15: With your ssh windows navigated to `your_project_folder_name`, run the following command: `mvn clean install`. This will build the Maven project, including dependencies for the Java AWS SDK. Do this for both EC2s.
- If you encounter any Java compile-time errors during this process, fix the errors on your local carfinder/textfinder then repeat Step 14. This bullet point is relevant if you made any custom changes to the code; the base code provided should compile without issue.

16: Once both projects (one per EC2 instance) are built fully, you can run each project using the following command: `mvn exec:java -Dexec.mainClass="carfinder"`. Replace carfinder with textfinder as necessary.
17: Watch the cmd windows as the programs print out debug, report, and update lines. Carfinder should report image classifier labels that Rekognition found in the S3 bucket images, while Textfinder should report messages consumed from the SQS queue.
18: On the ssh cmd window for textfinder, use the command `cat output.txt` to read a report of images containing cars and any text found within the images.
19: Once you are done, remember to stop all EC2 instances and turn off the AWS Academy lab. Otherwise, you may incur unexpected fees.

## Demonstration Link

The version of this README submitted for professor review contains a YouTube link to a video demonstrating compilation and runtime for this project. This README does not contain the link for privacy reasons.