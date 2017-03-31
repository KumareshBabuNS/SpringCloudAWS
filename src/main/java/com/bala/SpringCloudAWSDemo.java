package com.bala;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.aws.context.config.annotation.EnableContextInstanceData;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.bala.bean.EC2MetaInfo;
import com.bala.bean.S3Request;
import com.bala.bean.S3Response;


@SpringBootApplication
@RestController
@EnableContextInstanceData
public class SpringCloudAWSDemo {


	@Autowired
	private AmazonS3 amazonS3;

	@Autowired
	private AmazonSQS amazonSQS;


	@Value("${ami-id:N/A}")
	private String amiId;

	@Value("${hostname:N/A}")
	private String hostname;

	@Value("${instance-type:N/A}")
	private String instanceType;

	@Value("${services/domain:N/A}")
	private String serviceDomain;


	private static final Logger logger = LoggerFactory.getLogger(SpringCloudAWSDemo.class);

	public static void main(String[] args){

		SpringApplication.run(SpringCloudAWSDemo.class, args);
		logger.info("Application started");
	}

	@EventListener
	public void onStartUp(ContextRefreshedEvent event){

		logger.info("Spring Context Initialized");
	}



	@RequestMapping(value="/listS3Buckets", produces={"application/json"},  method = RequestMethod.GET)
	public List<String> listBuckets() {

		logger.info("listBuckets started");
		List<String> list = new ArrayList<String>();

		for (Bucket bucket:amazonS3.listBuckets()){
			list.add(bucket.getName());
		}

		logger.info("listBuckets end");
		return list;
	}


	@RequestMapping(value="/uploadImg", produces={"application/json"},  method = RequestMethod.POST)
	public S3Response uploadImage(@RequestBody S3Request s3Request) {
		logger.info("uploadImg started");

		File fileToUpload = new File(s3Request.getImgPath());
		String key = Instant.now().getEpochSecond() + "_" + fileToUpload.getName();

		//System.out.println(key)
		//amazonS3.putObject(new PutObjectRequest("balaji-aws-s3-0320", key, fileToUpload))

		TransferManager transferManager = new TransferManager(this.amazonS3);
		transferManager.upload("balaji-aws-s3-0320",s3Request.getImgName(),fileToUpload);
		S3Response res = new S3Response();
		res.setResult("upload done");
		logger.info("uploadImg end");



		return res;
	}


	@RequestMapping(value="/readImg", method=RequestMethod.GET)
	public void readImage(@RequestParam String imgName) throws IOException {
		logger.info("readImage started");

		S3Object s3 =amazonS3.getObject("balaji-aws-s3-0320", imgName);
		logger.info("Bucket name "+s3.getBucketName());

		InputStream stream= s3.getObjectContent();
		byte[] content = new byte[1024];

		RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

		HttpServletResponse servletResponse = ((ServletRequestAttributes) requestAttributes).getResponse();

		OutputStream outputStream= servletResponse.getOutputStream();


		int totalSize = 0;
		int bytesRead;
		while ((bytesRead = stream.read(content)) != -1) {
			//logger.info(String.format("%d bytes read from stream", bytesRead));
			outputStream.write(content, 0, bytesRead);
			totalSize += bytesRead;
		}
		logger.info("Total Size of file in bytes = "+totalSize);
		// close resource even during exception
		
		outputStream.flush();
		outputStream.close();
		
		logger.info("readImage end");

	}

	@RequestMapping(value="/setMsg", produces={"application/json"},  method = RequestMethod.GET)
	public String setMsgToQueue(@RequestParam String msg){
		logger.info("setMsgToQueue started");
		amazonSQS.sendMessage("MyQueue", msg);
		amazonSQS.sendMessage("NoListnerQueue", msg);
		logger.info("setMsgToQueue end");
		return "{\"success\":\"true\"}";
	}

	@RequestMapping(value="/retreiveEC2Info", produces={"application/json"},  method = RequestMethod.GET)
	public EC2MetaInfo retreiveEC2Info(){
		logger.info("retreiveEC2Info started");
		EC2MetaInfo meta = new EC2MetaInfo(amiId, hostname, instanceType, serviceDomain);
		logger.info("retreiveEC2Info end");
		return meta;
	}


	@RequestMapping(value="/retreiveQueueMessages", produces={"application/json"},  method = RequestMethod.GET)
	public List<String> sqsMessages(){
		logger.info("sqsMessages started");

		ReceiveMessageResult mesResult = amazonSQS.receiveMessage("NoListenerQueue");
		List<Message> list = mesResult.getMessages();
		List<String> mesList = new ArrayList<String>();

		logger.info("NoListnerQueue URL is "+amazonSQS.getQueueUrl("NoListenerQueue").getQueueUrl());
		list.forEach(
				message -> { 
					mesList.add(message.getBody());
					amazonSQS.deleteMessage(amazonSQS.getQueueUrl("NoListenerQueue").getQueueUrl(),message.getReceiptHandle());
				}
				);
		logger.info("sqsMessages started");
		return mesList;
	}

	/*@SqsListener("MyQueue")
	public void sqsList(String msg){
		logger.info("Message from MyQueue **  " +msg);

	}
	*/
	@RequestMapping(value="/createQueues",produces={"application/json"}, method=RequestMethod.GET)
	public String createQueues(){
		logger.info("createQueues started");
		amazonSQS.createQueue("MyQueue");
		amazonSQS.createQueue("NoListnerQueue");
		logger.info("createQueues end");
		return "{\"success\":\"true\"}";
		
	}
	
	@RequestMapping(value="/deleteQueues",produces={"application/json"}, method=RequestMethod.GET)
	public String deleteQueues(){
		logger.info("deleteQueues started");
		amazonSQS.deleteQueue("NoListenerQueue");
		logger.info("deleteQueues end");
		return "{\"success\":\"true\"}";
	}
}
