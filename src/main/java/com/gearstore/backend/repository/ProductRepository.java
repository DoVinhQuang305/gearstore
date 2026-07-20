package com.gearstore.backend.repository;

import com.gearstore.backend.entity.ProductEntity;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.util.Map;

import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primaryPartitionKey;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primarySortKey;

@Repository
public class ProductRepository {

    private final DynamoDbTable<ProductEntity> productTable;
    private final DynamoDbClient dynamoDbClient;

    public ProductRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;

        // Dùng StaticTableSchema để map thủ công, chính xác 100% tên cột DynamoDB
        StaticTableSchema<ProductEntity> schema = StaticTableSchema.builder(ProductEntity.class)
                .newItemSupplier(ProductEntity::new)
                .addAttribute(String.class, a -> a
                        .name("ProductId")
                        .getter(ProductEntity::getProductId)
                        .setter(ProductEntity::setProductId)
                        .tags(primaryPartitionKey()))
                .addAttribute(String.class, a -> a
                        .name("RecordType")
                        .getter(ProductEntity::getRecordType)
                        .setter(ProductEntity::setRecordType)
                        .tags(primarySortKey()))
                .addAttribute(String.class, a -> a
                        .name("Name")
                        .getter(ProductEntity::getName)
                        .setter(ProductEntity::setName))
                .addAttribute(Double.class, a -> a
                        .name("Price")
                        .getter(ProductEntity::getPrice)
                        .setter(ProductEntity::setPrice))
                .addAttribute(String.class, a -> a
                        .name("ImageUrl")   // Khớp chính xác tên cột trong DynamoDB (không có khoảng trắng)
                        .getter(ProductEntity::getImageUrl)
                        .setter(ProductEntity::setImageUrl))
                .addAttribute(
                        EnhancedType.mapOf(String.class, String.class),
                        a -> a.name("Attributes")
                                .getter(ProductEntity::getAttributes)
                                .setter(ProductEntity::setAttributes))
                .build();

        this.productTable = enhancedClient.table("GearStore_Products", schema);
    }

    // Lấy sản phẩm theo ProductId + RecordType
    public ProductEntity getProductById(String productId, String recordType) {
        Key key = Key.builder()
                .partitionValue(productId)
                .sortValue(recordType)
                .build();
        return productTable.getItem(key);
    }

    // Lấy tất cả sản phẩm bằng cách scan bảng
    public java.util.List<ProductEntity> getAllProducts() {
        return productTable.scan().items().stream().collect(java.util.stream.Collectors.toList());
    }

    // Lưu sản phẩm lên DynamoDB
    public void saveProduct(ProductEntity product) {
        productTable.putItem(product);
    }

    // Xóa sản phẩm khỏi DynamoDB
    public void deleteProduct(String productId, String recordType) {
        Key key = Key.builder()
                .partitionValue(productId)
                .sortValue(recordType)
                .build();
        productTable.deleteItem(key);
    }

    // Debug: lấy raw attributes từ DynamoDB để kiểm tra
    public Map<String, AttributeValue> getRawItem(String productId, String recordType) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName("GearStore_Products")
                .key(Map.of(
                        "ProductId", AttributeValue.fromS(productId),
                        "RecordType", AttributeValue.fromS(recordType)
                ))
                .build();
        return dynamoDbClient.getItem(request).item();
    }
}