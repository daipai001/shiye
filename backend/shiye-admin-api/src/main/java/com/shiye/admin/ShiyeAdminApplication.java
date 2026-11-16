package com.shiye.admin;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.shiye")
@MapperScan(basePackages = "com.shiye.admin.module", annotationClass = Mapper.class)
public class ShiyeAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiyeAdminApplication.class, args);
    }
}
