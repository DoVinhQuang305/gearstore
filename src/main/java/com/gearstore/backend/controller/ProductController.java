package com.gearstore.backend.controller;

import com.gearstore.backend.entity.ProductEntity;
import com.gearstore.backend.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;

    // Inject ProductRepository vào để dùng các hàm getProductById, saveProduct
    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // API lấy danh sách toàn bộ sản phẩm: GET http://localhost:8080/products
    @GetMapping
    public ResponseEntity<java.util.List<ProductEntity>> getAllProducts() {
        return ResponseEntity.ok(productRepository.getAllProducts());
    }

    // API lấy chi tiết sản phẩm: GET http://localhost:8080/products/LAP001?type=PRODUCT_INFO
    @GetMapping("/{id}")
    public ResponseEntity<ProductEntity> getProductDetails(
            @PathVariable("id") String productId,
            @RequestParam(value = "type", defaultValue = "PRODUCT_INFO") String recordType) {

        ProductEntity product = productRepository.getProductById(productId, recordType);

        if (product != null) {
            return ResponseEntity.ok(product);
        } else {
            return ResponseEntity.notFound().build(); // Trả về 404 nếu không tìm thấy máy trên AWS
        }
    }

    // API thêm mới sản phẩm: POST http://localhost:8080/products
    @PostMapping
    public ResponseEntity<String> createProduct(@RequestBody ProductEntity product) {
        productRepository.saveProduct(product);
        return ResponseEntity.ok("Product saved successfully to AWS DynamoDB!");
    }

    // API cập nhật sản phẩm: PUT http://localhost:8080/products/{id}
    @PutMapping("/{id}")
    public ResponseEntity<String> updateProduct(
            @PathVariable("id") String productId,
            @RequestBody ProductEntity product) {
        product.setProductId(productId);
        productRepository.saveProduct(product);
        return ResponseEntity.ok("Product updated successfully in AWS DynamoDB!");
    }

    // API xóa sản phẩm: DELETE http://localhost:8080/products/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteProduct(
            @PathVariable("id") String productId,
            @RequestParam(value = "type", defaultValue = "PRODUCT_INFO") String recordType) {
        productRepository.deleteProduct(productId, recordType);
        return ResponseEntity.ok("Product deleted successfully from AWS DynamoDB!");
    }

    // API DEBUG: xem raw attributes thực tế từ DynamoDB
    // GET http://localhost:8080/products/debug/LAP001
    @GetMapping("/debug/{id}")
    public ResponseEntity<?> debugRawItem(
            @PathVariable("id") String productId,
            @RequestParam(value = "type", defaultValue = "PRODUCT_INFO") String recordType) {
        var raw = productRepository.getRawItem(productId, recordType);
        // Convert AttributeValue sang String để đọc được
        java.util.Map<String, String> readable = new java.util.LinkedHashMap<>();
        raw.forEach((key, val) -> {
            String value = val.s() != null ? val.s()
                    : val.n() != null ? val.n()
                    : val.m() != null ? val.m().toString()
                    : val.toString();
            readable.put(key, value);
        });
        return ResponseEntity.ok(readable);
    }

    @Value("${aws.dynamodb.accesskey}")
    private String accessKey;

    @Value("${aws.dynamodb.secretkey}")
    private String secretKey;

    @Value("${aws.dynamodb.region}")
    private String regionStr;

    private static final String BUCKET_NAME = "gearstore-data-images";

    // API Upload ảnh lên AWS S3: POST http://localhost:8080/products/upload
    @PostMapping("/upload")
    public ResponseEntity<String> uploadProductImage(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File rỗng!");
        }

        try {
            // Khởi tạo S3 Client
            software.amazon.awssdk.services.s3.S3Client s3Client = software.amazon.awssdk.services.s3.S3Client.builder()
                    .region(software.amazon.awssdk.regions.Region.of(regionStr))
                    .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .build();

            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            
            // Upload file lên S3
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("upload_", fileName);
            file.transferTo(tempFile.toFile());

            try {
                // Thử upload với public read ACL
                s3Client.putObject(
                        software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                                .bucket(BUCKET_NAME)
                                .key(fileName)
                                .contentType(file.getContentType())
                                .acl(software.amazon.awssdk.services.s3.model.ObjectCannedACL.PUBLIC_READ)
                                .build(),
                        tempFile
                );
            } catch (Exception e) {
                // Nếu bị cấm ACL (block public access), upload thường
                s3Client.putObject(
                        software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                                .bucket(BUCKET_NAME)
                                .key(fileName)
                                .contentType(file.getContentType())
                                .build(),
                        tempFile
                );
            }

            java.nio.file.Files.delete(tempFile); // Xóa file tạm

            String s3Url = "https://" + BUCKET_NAME + ".s3." + regionStr + ".amazonaws.com/" + fileName;
            return ResponseEntity.ok(s3Url);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Lỗi khi upload ảnh lên S3: " + e.getMessage());
        }
    }
}