package com.gearstore.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.springframework.util.StringUtils;

@Configuration
public class DynamoDbConfig {

    @Value("${aws.dynamodb.region}")
    private String region;

    // Khi chạy local: đọc từ application.properties
    // Khi chạy trên Lambda: để trống, SDK tự dùng IAM Role
    @Value("${aws.dynamodb.accesskey:}")
    private String accessKey;

    @Value("${aws.dynamodb.secretkey:}")
    private String secretKey;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region));

        // Nếu có access key (chạy local) → dùng StaticCredentials
        // Nếu không có (chạy trên Lambda với IAM Role) → dùng DefaultCredentials (tự động)
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                )
            );
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}