package org.shaolin.bmdp.spcould;

import org.shaolin.bmdp.runtime.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class UIMasterMessageQueue {
	private static final Logger logger = LoggerFactory.getLogger(UIMasterMessageQueue.class);
	
	public static void main(String[] args) {
		Registry registry = Registry.getInstance();
		registry.initRegistry();

		

		SpringApplication.run(UIMasterMessageQueue.class, args);
	}
	
}
