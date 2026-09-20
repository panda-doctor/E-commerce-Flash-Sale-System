package com.ghb.ecommerceflashsalesystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@MapperScan("com.ghb.ecommerceflashsalesystem.mapper")
@EnableScheduling
@SpringBootApplication
public class ECommerceFlashSaleSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceFlashSaleSystemApplication.class, args);
    }

}
