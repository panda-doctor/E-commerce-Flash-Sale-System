package com.ghb.ecommerceflashsalesystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@MapperScan
@SpringBootApplication
public class ECommerceFlashSaleSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceFlashSaleSystemApplication.class, args);
    }

}
