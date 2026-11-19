package com.teaclub.cloud.messaging.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan(basePackages = {
        "com.teaclub.cloud.messaging.config",
        "com.teaclub.cloud.messaging.publisher",
        "com.teaclub.cloud.messaging.consumer"
})
public class TeaClubMessagingAutoConfiguration {
}
