package com.gearstore.backend.controller;

import com.gearstore.backend.entity.ProductEntity;
import com.gearstore.backend.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;

    // Inject ProductRepository vào để dùng các hàm getProductById, saveProduct
    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
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
}