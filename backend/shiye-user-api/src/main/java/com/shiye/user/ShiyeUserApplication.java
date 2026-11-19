package com.shiye.user;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.shiye")
@MapperScan(basePackages = {
        "com.shiye.user.module",
        "com.shiye.user.common.ai.config",
        "com.shiye.user.common.settings"
}, annotationClass = Mapper.class)
public class ShiyeUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiyeUserApplication.class, args);
    }
}
