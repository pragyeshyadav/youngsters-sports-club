package com.youngstersclub.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = "com.youngstersclub.app.entity")
@EnableJpaRepositories(basePackages = "com.youngstersclub.app.repository")
@EnableScheduling
@EnableAsync
public class YoungstersSportsClubApplication {

  private static final Logger log = LoggerFactory.getLogger(YoungstersSportsClubApplication.class);

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(YoungstersSportsClubApplication.class);
    app.addListeners(
        (ApplicationListener<ApplicationReadyEvent>)
            event -> {
              Environment env = event.getApplicationContext().getEnvironment();
              String port = env.getProperty("server.port", "8080");
              log.info("Youngsters Sports Club ready — server.port={} (set PORT on Render)", port);
            });
    app.run(args);
  }
}
