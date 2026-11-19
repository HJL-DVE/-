package com.teaclub.cloud.messaging.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableScheduling
@ComponentScan(basePackages = {
        "com.teaclub.cloud.messaging.config",
        "com.teaclub.cloud.messaging.publisher",
        "com.teaclub.cloud.messaging.consumer",
        "com.teaclub.cloud.messaging.outbox",
        "com.teaclub.cloud.messaging.inbox",
        "com.teaclub.cloud.messaging.event"
})
public class TeaClubMessagingAutoConfiguration {
}
