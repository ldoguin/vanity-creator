package com.clevercloud.vanitygenerator;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.cloudinary.Cloudinary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexmo.client.NexmoClient;
import com.nexmo.client.auth.AuthMethod;
import com.nexmo.client.auth.TokenAuthMethod;
import com.nexmo.client.sms.SmsSubmissionResult;
import com.nexmo.client.sms.messages.TextMessage;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.client.CleverApiClient;
import io.swagger.client.api.DefaultApi;
import io.swagger.client.model.ApplicationView;
import io.swagger.client.model.AvailableInstanceView;
import io.swagger.client.model.WannabeApplication;
import io.swagger.client.model.WannabeValue;

@SpringBootApplication
@Controller
public class VanityGeneratorApplication {

	@Value("${clevercloud.targetorg:}")
	String ORGA_ID;
	@Value("${nexmo.api.key:}")
	String NEXMO_API_KEY;
	@Value("${nexmo.api.secret:}")
	String NEXMO_API_SECRET;
	@Value("${nexmo.number.from:}")
	String FROM_NUMBER;

	@Value("${cloudinary.api.ke:}")
	String CLOUDINARY_API_KEY;
	@Value("${cloudinary.api.secret:}")
	String CLOUDINARY_API_SECRET;
	@Value("${cloudinary.api.name:}")
	String CLOUDINARY_API_NAME;


	@Value("${clevercloud.consumerKey:}")
    String consumerKey;
    @Value("${clevercloud.consumerSecret:}")
    String consumerSecret;
    @Value("${clevercloud.token:}")
    String token;
    @Value("${clevercloud.tokenSecret:}")
    String tokenSecret;

	@Bean
	public DefaultApi apiClient(){
        CleverApiClient apiClient = new CleverApiClient(consumerKey, consumerSecret, token, tokenSecret);
        apiClient.setBasePath("https://api.clever-cloud.com/v2");
        return new DefaultApi(apiClient);
	}

	@Bean
	public NexmoClient nexmoClient(){
		AuthMethod auth = new TokenAuthMethod(NEXMO_API_KEY, NEXMO_API_SECRET);
		return new NexmoClient(auth);
	}

	@Bean
	public Cloudinary cloudinaryClient(){
		Map<String, String> config = new HashMap<String, String>();
		config.put("cloud_name", CLOUDINARY_API_NAME);
		config.put("api_key", CLOUDINARY_API_KEY);
		config.put("api_secret", CLOUDINARY_API_SECRET);
		return new Cloudinary(config);
	}

	@PostMapping(	
		value="/hookmeup", 
		produces={MediaType.APPLICATION_JSON_VALUE},
		consumes={MediaType.APPLICATION_JSON_VALUE}	
	)
	@ResponseBody
	public String hookmeup(@RequestBody String payload) throws Exception{
		ObjectMapper om = new ObjectMapper();
		JsonNode json = om.readTree(payload);
		JsonNode answers = json.get("form_response").get("answers");
		String email = answers.get(2).get("email").asText();
		String firstname = answers.get(0).get("text").asText();
		String lastname = answers.get(1).get("text").asText();
		String phone = answers.get(3).get("text").asText();
		String url = answers.get(4).get("file_url").asText();
		Map cloudinaryOptions = uploadToCloudinary(url);
		System.out.println(cloudinaryOptions);
		String cloudinaryURL = (String)cloudinaryOptions.get("url");
		createVanityApp(firstname, lastname, phone, cloudinaryURL);
		return "{}";
	}

	@GetMapping("/")
	@ResponseBody
	public String index(){
		return "ok";
	}

	public static void main(String[] args) {
		SpringApplication.run(VanityGeneratorApplication.class, args);
	}



    public void createVanityApp(String firstname, String lastname, String phone, String url) throws Exception {
		String appId = String.format("%s-%s", firstname.toLowerCase(), lastname.toLowerCase());
		String domain = String.format("%s.cleverapps.io", appId);
		DefaultApi api = apiClient();
        //create a Static Application
        WannabeApplication app = new WannabeApplication();
        app.setName("VanityApp" + firstname + lastname);
        AvailableInstanceView goinstance = api.getAvailableInstances("").stream().filter((instance) ->
        {return instance.getName().equals("Node") && instance.getType().equals("node");}).findFirst().get();
        System.out.println(goinstance);
        app.setArchived(false);
        app.setCancelOnPush(true);
        app.setDescription("Vanity app for " + firstname);
        app.setZone("par");
        app.setFavourite(true);
        app.setMaxFlavor(goinstance.getDefaultFlavor().getName());
        app.setMinFlavor(goinstance.getDefaultFlavor().getName());
        app.setStickySessions(false);
        app.setMinInstances(1);
        app.setMaxInstances(1);
        app.setShutdownable(true);
        app.setSeparateBuild(false);
        app.setDeploy("git");
        app.setTags(Arrays.asList("API","TEST"));
        app.setInstanceType(goinstance.getType());
        app.setInstanceVariant(goinstance.getVariant().getId());
        app.setInstanceVersion(goinstance.getVersion());
		
		ApplicationView createdApp = api.addApplication(app, ORGA_ID);
		ApplicationView fetchedApp = api.getApplication(ORGA_ID, createdApp.getId());
		String gitRemote = fetchedApp.getDeployUrl();

		WannabeValue v = new WannabeValue();
		v.setValue(firstname);
		api.editApplicationEnv(v,ORGA_ID, fetchedApp.getId(), "FIRST_NAME");
		v.setValue(lastname);
		api.editApplicationEnv(v,ORGA_ID, fetchedApp.getId(), "LAST_NAME");
		v.setValue(url);
		api.editApplicationEnv(v,ORGA_ID, fetchedApp.getId(), "URL");
		api.addVhost(ORGA_ID,  fetchedApp.getId(),domain);
		sendText(phone, "https://"+domain);
        Unreliables.retryUntilSuccess(10, TimeUnit.SECONDS, () -> {
			int code = push(gitRemote);
			System.out.println(code);
			if ( 0 !=  code ){  return true;}
			return false;
		});
	}
	

	public int push(String remote) throws Exception {
		String filepath = System.getProperty("user.dir") + "/gitrepo";
		System.out.println(filepath);
		File workingDirectory = new File(filepath);
		String line = String.format("git push %s master", remote);
		CommandLine cmdLine = CommandLine.parse(line);
		DefaultExecutor executor = new DefaultExecutor();
		executor.setWorkingDirectory(workingDirectory);
		executor.setExitValue(0);
		ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
		executor.setWatchdog(watchdog);
		return executor.execute(cmdLine);
	}

	public void sendText(String phoneNumber, String appURL) throws Exception {
		TextMessage message = new TextMessage(FROM_NUMBER, phoneNumber, "Your app is ready! Go to " + appURL);
		SmsSubmissionResult[] responses = nexmoClient().getSmsClient().submitMessage(message);
		for (SmsSubmissionResult response : responses) {
			System.out.println(response);
		}
	}


	public Map uploadToCloudinary(String url) throws Exception {
		return cloudinaryClient().uploader().upload(url, Collections.EMPTY_MAP);
	}

}
