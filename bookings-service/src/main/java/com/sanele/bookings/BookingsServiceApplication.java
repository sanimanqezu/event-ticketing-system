package com.sanele.bookings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class BookingsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingsServiceApplication.class, args);
    }
}
