package com.gearstore.backend.repository;

import com.gearstore.backend.entity.UserEntity;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primaryPartitionKey;

@Repository
public class UserRepository {

    private final DynamoDbTable<UserEntity> userTable;

    public UserRepository(DynamoDbEnhancedClient enhancedClient) {
        StaticTableSchema<UserEntity> schema = StaticTableSchema.builder(UserEntity.class)
                .newItemSupplier(UserEntity::new)
                .addAttribute(String.class, a -> a
                        .name("Username")
                        .getter(UserEntity::getUsername)
                        .setter(UserEntity::setUsername)
                        .tags(primaryPartitionKey()))
                .addAttribute(String.class, a -> a
                        .name("Password")
                        .getter(UserEntity::getPassword)
                        .setter(UserEntity::setPassword))
                .addAttribute(String.class, a -> a
                        .name("FullName")
                        .getter(UserEntity::getFullName)
                        .setter(UserEntity::setFullName))
                .addAttribute(String.class, a -> a
                        .name("Role")
                        .getter(UserEntity::getRole)
                        .setter(UserEntity::setRole))
                .addAttribute(String.class, a -> a
                        .name("Email")
                        .getter(UserEntity::getEmail)
                        .setter(UserEntity::setEmail))
                .build();

        this.userTable = enhancedClient.table("GearStore_Users", schema);
    }

    // Tạo bảng nếu chưa tồn tại
    public void createTableIfNotExists() {
        try {
            userTable.createTable();
            System.out.println("✅ Bang GearStore_Users khoi tao thanh cong tren DynamoDB!");
        } catch (ResourceInUseException e) {
            System.out.println("ℹ️ Bang GearStore_Users da co san tren DynamoDB.");
        } catch (Exception e) {
            System.out.println("⚠️ Khong the tao bang: " + e.getMessage());
        }
    }

    // Lưu user vào bảng
    public void saveUser(UserEntity user) {
        userTable.putItem(user);
    }

    // Lấy user theo username
    public UserEntity getUser(String username) {
        try {
            return userTable.getItem(r -> r.key(k -> k.partitionValue(username)));
        } catch (Exception e) {
            return null;
        }
    }

    // Lấy tất cả user (Scan DynamoDB)
    public java.util.List<UserEntity> getAllUsers() {
        try {
            return userTable.scan().items().stream().collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }
}
