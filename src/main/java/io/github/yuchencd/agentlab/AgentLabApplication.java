package io.github.yuchencd.agentlab;

import io.github.yuchencd.agentlab.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class AgentLabApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentLabApplication.class, args);
  }
}
