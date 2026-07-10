package com.gearstore.backend.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Map;

@DynamoDbBean
public class ProductEntity {

    private String productId;
    private String recordType;
    private String name;
    private Double price;
    private String imageUrl; // Link ảnh từ S3
    private Map<String, String> attributes; // Hộp chứa các thông số động (CPU, RAM, VGA...)

    // ===== Constructors =====
    public ProductEntity() {}

    // ===== ProductId (Partition Key) =====
    @DynamoDbPartitionKey
    @DynamoDbAttribute("ProductId")
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    // ===== RecordType (Sort Key) =====
    @DynamoDbSortKey
    @DynamoDbAttribute("RecordType")
    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }

    // ===== Name =====
    @DynamoDbAttribute("Name")
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // ===== Price =====
    @DynamoDbAttribute("Price")
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    // ===== ImageUrl =====
    @DynamoDbAttribute("ImageUrl")
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    // ===== Attributes =====
    @DynamoDbAttribute("Attributes")
    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
}