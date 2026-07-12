package org.feiesos.asynctaskhub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("org.feiesos.asynctaskhub.mapper")
@EnableScheduling
public class AsyncTaskHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsyncTaskHubApplication.class, args);
    }
}
