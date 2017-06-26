package org.shaolin.bmdp.spcould;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableConfigServer
@EnableEurekaServer
public class UIServiceCenter {

	public static void main(String[] args) {
		SpringApplication.run(UIServiceCenter.class, args);
	}

}
