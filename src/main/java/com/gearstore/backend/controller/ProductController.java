package com.gearstore.backend.controller;

import com.gearstore.backend.entity.ProductEntity;
import com.gearstore.backend.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;

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
            return ResponseEntity.notFound().build();
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

        ProductEntity oldProduct = productRepository.getProductById(productId, product.getRecordType());

        if (oldProduct != null && oldProduct.getImageUrl() != null && !oldProduct.getImageUrl().isEmpty()) {
            if (!oldProduct.getImageUrl().equals(product.getImageUrl())) {
                try {
                    String oldUrl = oldProduct.getImageUrl();
                    if (oldUrl.contains(".amazonaws.com/")) {
                        String oldKey = oldUrl.substring(oldUrl.lastIndexOf("/") + 1);
                        deleteS3Object(oldKey);
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi khi tự động xóa ảnh cũ trên S3: " + e.getMessage());
                }
            }
        }

        product.setProductId(productId);
        productRepository.saveProduct(product);
        return ResponseEntity.ok("Product updated successfully in AWS DynamoDB!");
    }

    // API xóa sản phẩm: DELETE http://localhost:8080/products/{id}?type=PRODUCT_INFO
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteProduct(
            @PathVariable("id") String productId,
            @RequestParam(value = "type", defaultValue = "PRODUCT_INFO") String recordType) {

        ProductEntity oldProduct = productRepository.getProductById(productId, recordType);

        if (oldProduct != null && oldProduct.getImageUrl() != null && !oldProduct.getImageUrl().isEmpty()) {
            try {
                String oldUrl = oldProduct.getImageUrl();
                if (oldUrl.contains(".amazonaws.com/")) {
                    String oldKey = oldUrl.substring(oldUrl.lastIndexOf("/") + 1);
                    deleteS3Object(oldKey);
                }
            } catch (Exception e) {
                System.err.println("Lỗi khi tự động xóa ảnh cũ trên S3 khi xóa sản phẩm: " + e.getMessage());
            }
        }

        productRepository.deleteProduct(productId, recordType);
        return ResponseEntity.ok("Product deleted successfully from AWS DynamoDB!");
    }

    // API xóa file tạm S3 trực tiếp
    @DeleteMapping("/s3-delete")
    public ResponseEntity<String> deleteS3ObjectApi(@RequestParam("key") String key) {
        try {
            if (key != null && !key.isEmpty()) {
                deleteS3Object(key);
                return ResponseEntity.ok("Xóa file tạm trên S3 thành công!");
            }
            return ResponseEntity.badRequest().body("Key không được để trống!");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Lỗi khi xóa file trên S3: " + e.getMessage());
        }
    }

    // API debug để xem cấu trúc item DynamoDB thô
    @GetMapping("/{id}/debug")
    public ResponseEntity<?> debugRawItem(
            @PathVariable("id") String productId,
            @RequestParam(value = "type", defaultValue = "PRODUCT_INFO") String recordType) {
        var raw = productRepository.getRawItem(productId, recordType);
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

    // Hàm phụ trợ xóa file trên AWS S3
    private void deleteS3Object(String key) {
        try {
            software.amazon.awssdk.services.s3.S3Client s3Client = software.amazon.awssdk.services.s3.S3Client.builder()
                    .region(software.amazon.awssdk.regions.Region.of(regionStr))
                    .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .build();

            s3Client.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(key)
                    .build());
            System.out.println("Đã xóa file cũ trên S3: " + key);
        } catch (Exception e) {
            System.err.println("Lỗi khi gọi amazonS3.deleteObject cho key " + key + ": " + e.getMessage());
        }
    }

    // API Upload ảnh lên AWS S3: POST http://localhost:8080/products/upload
    @PostMapping("/upload")
    public ResponseEntity<String> uploadProductImage(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File rỗng!");
        }

        try {
            software.amazon.awssdk.services.s3.S3Client s3Client = software.amazon.awssdk.services.s3.S3Client.builder()
                    .region(software.amazon.awssdk.regions.Region.of(regionStr))
                    .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .build();

            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("upload_", fileName);
            file.transferTo(tempFile.toFile());

            try {
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
                s3Client.putObject(
                        software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                                .bucket(BUCKET_NAME)
                                .key(fileName)
                                .contentType(file.getContentType())
                                .build(),
                        tempFile
                );
            }

            java.nio.file.Files.delete(tempFile);
            String s3Url = "https://" + BUCKET_NAME + ".s3." + regionStr + ".amazonaws.com/" + fileName;
            return ResponseEntity.ok(s3Url);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Lỗi khi upload ảnh lên S3: " + e.getMessage());
        }
    }

    // Helper dọn dẹp sạch toàn bộ file trong S3 bucket để giải phóng dung lượng
    private void cleanS3Bucket() {
        try {
            software.amazon.awssdk.services.s3.S3Client s3Client = software.amazon.awssdk.services.s3.S3Client.builder()
                    .region(software.amazon.awssdk.regions.Region.of(regionStr))
                    .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .build();

            software.amazon.awssdk.services.s3.model.ListObjectsV2Response listResponse = s3Client.listObjectsV2(
                    software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder().bucket(BUCKET_NAME).build()
            );

            java.util.List<software.amazon.awssdk.services.s3.model.S3Object> objects = listResponse.contents();
            if (objects != null && !objects.isEmpty()) {
                System.out.println("Đang xóa " + objects.size() + " files cũ trên S3...");
                for (software.amazon.awssdk.services.s3.model.S3Object obj : objects) {
                    s3Client.deleteObject(
                            software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                                    .bucket(BUCKET_NAME)
                                    .key(obj.key())
                                    .build()
                    );
                }
                System.out.println("Đã dọn dẹp sạch S3 bucket!");
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi dọn dẹp S3: " + e.getMessage());
        }
    }

    // Helper tải ảnh từ URL và upload trực tiếp lên S3
    private String downloadAndUploadToS3(String imageUrl, String productId) {
        try {
            software.amazon.awssdk.services.s3.S3Client s3Client = software.amazon.awssdk.services.s3.S3Client.builder()
                    .region(software.amazon.awssdk.regions.Region.of(regionStr))
                    .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .build();

            // Sử dụng HttpClient hỗ trợ follow redirect tự động
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                    .build();

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(imageUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .GET()
                    .build();

            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("seed_" + productId + "_", ".jpg");
            java.net.http.HttpResponse<java.nio.file.Path> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofFile(tempFile));

            if (response.statusCode() != 200) {
                java.nio.file.Files.deleteIfExists(tempFile);
                throw new java.io.IOException("HTTP error code: " + response.statusCode());
            }

            String fileName = "seed_" + productId + "_" + System.currentTimeMillis() + ".jpg";

            try {
                s3Client.putObject(
                        software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                                .bucket(BUCKET_NAME)
                                .key(fileName)
                                .contentType("image/jpeg")
                                .acl(software.amazon.awssdk.services.s3.model.ObjectCannedACL.PUBLIC_READ)
                                .build(),
                        tempFile
                );
            } catch (Exception e) {
                s3Client.putObject(
                        software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                                .bucket(BUCKET_NAME)
                                .key(fileName)
                                .contentType("image/jpeg")
                                .build(),
                        tempFile
                );
            }

            java.nio.file.Files.delete(tempFile);
            return "https://" + BUCKET_NAME + ".s3." + regionStr + ".amazonaws.com/" + fileName;

        } catch (Exception e) {
            System.err.println("Không tải được ảnh mẫu " + imageUrl + " cho " + productId + ", dùng link gốc. Lỗi: " + e.getMessage());
            return imageUrl;
        }
    }

    // API reset database - chạy bất đồng bộ, trả về ngay lập tức
    @PostMapping(value = "/reset-database", produces = "text/plain; charset=UTF-8")
    public ResponseEntity<String> resetDatabase() {
        // Khởi động tiến trình seed ngầm
        new Thread(this::runSeedInBackground).start();
        return ResponseEntity.ok("✅ Đang xử lý: Dọn S3 bucket và seed 110 sản phẩm mẫu trong nền. Vui lòng đợi 3-5 phút rồi tải lại trang!");
    }

    // Hàm seed chạy ngầm trong background thread
    private void runSeedInBackground() {

        cleanS3Bucket();

        try {
            java.util.List<ProductEntity> all = productRepository.getAllProducts();
            for (ProductEntity p : all) {
                productRepository.deleteProduct(p.getProductId(), p.getRecordType());
            }
        } catch (Exception e) {
            System.err.println("Lỗi dọn DB: " + e.getMessage());
        }

        java.util.function.Consumer<ProductEntity> save = productRepository::saveProduct;

        // 1. LAPTOP (Ảnh thật laptop từ Unsplash)
        String[][] laptops = {
            {"LAP001","Laptop ASUS Vivobook 14 OLED","14500000","https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=600&h=400&fit=crop","ASUS","Intel Core i5-1235U","8GB DDR4","512GB NVMe SSD","Intel Iris Xe Graphics","14.0 inch FHD OLED","12 tháng","Windows 11 Home"},
            {"LAP002","Laptop Acer Aspire 5 A515-58M","12900000","https://images.unsplash.com/photo-1525547719571-a2d4ac8945e2?w=600&h=400&fit=crop","Acer","Intel Core i3-1305U","8GB DDR4","256GB NVMe SSD","Intel UHD Graphics","15.6 inch FHD IPS","12 tháng","Windows 11 Home"},
            {"LAP003","Laptop HP Pavilion 15-eg3066TX","16200000","https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=600&h=400&fit=crop","HP","Intel Core i5-1335U","16GB DDR4","512GB NVMe SSD","Intel Iris Xe Graphics","15.6 inch FHD IPS","12 tháng","Windows 11 Home"},
            {"LAP004","Laptop Dell Inspiron 15 3530","15500000","https://images.unsplash.com/photo-1593642632559-0c6d3fc62b89?w=600&h=400&fit=crop","Dell","Intel Core i5-1334U","8GB DDR4","512GB NVMe SSD","Intel UHD Graphics","15.6 inch FHD 120Hz","12 tháng","Windows 11 Home"},
            {"LAP005","Laptop Lenovo IdeaPad Slim 3 15ABR8","13800000","https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=600&h=400&fit=crop","Lenovo","AMD Ryzen 5 7530U","8GB DDR4","512GB NVMe SSD","AMD Radeon Graphics","15.6 inch FHD IPS","12 tháng","Windows 11 Home"},
            {"LAP006","Apple MacBook Air 13 M2 8GB/256GB","26500000","https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?w=600&h=400&fit=crop","Apple","Apple M2 8-core CPU","8GB Unified Memory","256GB NVMe SSD","Apple GPU 8-core","13.6 inch Liquid Retina","12 tháng","macOS Sonoma"},
            {"LAP007","Laptop ASUS Zenbook 14 OLED UX3405MA","24900000","https://images.unsplash.com/photo-1484788984921-03950022c38b?w=600&h=400&fit=crop","ASUS","Intel Core Ultra 7 155H","16GB LPDDR5X","512GB NVMe SSD","Intel Arc Graphics","14.0 inch 3K OLED 120Hz","12 tháng","Windows 11 Home"},
            {"LAP008","Laptop Dell Vostro 14 3430","17400000","https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&h=400&fit=crop","Dell","Intel Core i5-1334U","16GB DDR4","512GB NVMe SSD","Intel Iris Xe Graphics","14.0 inch FHD IPS","12 tháng","Windows 11 Pro"},
            {"LAP009","Laptop HP ProBook 450 G10 9H8G1PT","19800000","https://images.unsplash.com/photo-1560472354-b33ff0c44a43?w=600&h=400&fit=crop","HP","Intel Core i5-1335U","16GB DDR4","512GB NVMe SSD","Intel Iris Xe Graphics","15.6 inch FHD IPS","12 tháng","Windows 11 Pro"},
            {"LAP010","Laptop Lenovo ThinkPad E14 Gen 5","22500000","https://images.unsplash.com/photo-1448932223592-d1fc686e76ea?w=600&h=400&fit=crop","Lenovo","Intel Core i7-1355U","16GB DDR4","512GB NVMe SSD","Intel Iris Xe Graphics","14.0 inch WUXGA IPS","12 tháng","Windows 11 Pro"}
        };
        for (String[] d : laptops) {
            ProductEntity p = new ProductEntity(); p.setProductId(d[0]); p.setRecordType("PRODUCT_INFO"); p.setName(d[1]); p.setPrice(Double.parseDouble(d[2]));
            p.setImageUrl(downloadAndUploadToS3(d[3], d[0]));
            java.util.Map<String,String> a = new java.util.HashMap<>();
            a.put("Hãng",d[4]); a.put("CPU",d[5]); a.put("RAM",d[6]); a.put("Ổ cứng",d[7]); a.put("VGA",d[8]); a.put("Màn hình",d[9]); a.put("Bảo hành",d[10]); a.put("Hệ điều hành",d[11]);
            p.setAttributes(a); save.accept(p);
        }

        // 2. LAPTOP GAMING (Ảnh thật Gaming Laptop từ Unsplash)
        String[][] gamings = {
            {"LPG001","Laptop Gaming ASUS TUF A15 FA507NV","21900000","https://images.unsplash.com/photo-1603302576837-37561b2e2302?w=600&h=400&fit=crop","ASUS","AMD Ryzen 5 7535HS","16GB DDR5","512GB NVMe SSD","NVIDIA RTX 4060 8GB","15.6 inch FHD IPS","144Hz","12 tháng","Windows 11 Home"},
            {"LPG002","Laptop Gaming Acer Nitro 5 AN515-58","22500000","https://images.unsplash.com/photo-1593640408182-31c70c8268f5?w=600&h=400&fit=crop","Acer","Intel Core i5-12500H","8GB DDR4","512GB NVMe SSD","NVIDIA RTX 3050 Ti 4GB","15.6 inch FHD IPS","144Hz","12 tháng","Windows 11 Home"},
            {"LPG003","Laptop Gaming Lenovo LOQ 15IAX9","23400000","https://images.unsplash.com/photo-1542393545-10f5cde2c810?w=600&h=400&fit=crop","Lenovo","Intel Core i5-12450HX","16GB DDR5","512GB NVMe SSD","NVIDIA RTX 4050 6GB","15.6 inch FHD IPS","144Hz","12 tháng","Windows 11 Home"},
            {"LPG004","Laptop Gaming MSI Cyborg 15 A12VF","20800000","https://images.unsplash.com/photo-1612287230202-1ff1d85d1bdf?w=600&h=400&fit=crop","MSI","Intel Core i5-12450H","8GB DDR4","512GB NVMe SSD","NVIDIA RTX 4060 8GB","15.6 inch FHD IPS","144Hz","12 tháng","Windows 11 Home"},
            {"LPG005","Laptop Gaming Gigabyte G5 MF5","19500000","https://images.unsplash.com/photo-1598550476439-6847785fcea6?w=600&h=400&fit=crop","Gigabyte","Intel Core i5-12500H","8GB DDR4","512GB NVMe SSD","NVIDIA RTX 4050 6GB","15.6 inch FHD IPS","144Hz","12 tháng","Windows 11 Home"},
            {"LPG006","Laptop Gaming ASUS ROG Strix G16","36900000","https://images.unsplash.com/photo-1616588589676-62b3bd4ff6d2?w=600&h=400&fit=crop","ASUS","Intel Core i7-13650HX","16GB DDR5","512GB NVMe SSD","NVIDIA RTX 4060 8GB","16 inch FHD+ IPS","165Hz","12 tháng","Windows 11 Home"},
            {"LPG007","Laptop Gaming Acer Predator Helios Neo","34500000","https://images.unsplash.com/photo-1593642634315-48f5414c3ad9?w=600&h=400&fit=crop","Acer","Intel Core i7-13700HX","16GB DDR5","512GB NVMe SSD","NVIDIA RTX 4060 8GB","16 inch WUXGA IPS","165Hz","12 tháng","Windows 11 Home"},
            {"LPG008","Laptop Gaming Lenovo Legion 5 15IAH7H","38200000","https://images.unsplash.com/photo-1547394765-185e1e68f34e?w=600&h=400&fit=crop","Lenovo","Intel Core i7-12700H","16GB DDR5","512GB NVMe SSD","NVIDIA RTX 4060 8GB","15.6 inch FHD IPS","165Hz","12 tháng","Windows 11 Home"},
            {"LPG009","Laptop Gaming MSI Katana 15 B13VEK","25900000","https://images.unsplash.com/photo-1609240038960-aa89b5745ccb?w=600&h=400&fit=crop","MSI","Intel Core i7-13620H","16GB DDR5","512GB NVMe SSD","NVIDIA RTX 4050 6GB","15.6 inch FHD IPS","144Hz","12 tháng","Windows 11 Home"},
            {"LPG010","Laptop Gaming HP Victus 16-r0116TX","24200000","https://images.unsplash.com/photo-1574158622682-e40e69881006?w=600&h=400&fit=crop","HP","Intel Core i5-13500H","16GB DDR5","512GB NVMe SSD","NVIDIA RTX 4050 6GB","16.1 inch FHD IPS","144Hz","12 tháng","Windows 11 Home"}
        };
        for (String[] d : gamings) {
            ProductEntity p = new ProductEntity(); p.setProductId(d[0]); p.setRecordType("PRODUCT_INFO"); p.setName(d[1]); p.setPrice(Double.parseDouble(d[2]));
            p.setImageUrl(downloadAndUploadToS3(d[3], d[0]));
            java.util.Map<String,String> a = new java.util.HashMap<>();
            a.put("Hãng",d[4]); a.put("CPU",d[5]); a.put("RAM",d[6]); a.put("Ổ cứng",d[7]); a.put("VGA",d[8]); a.put("Màn hình",d[9]); a.put("Tần số quét",d[10]); a.put("Bảo hành",d[11]); a.put("Hệ điều hành",d[12]);
            p.setAttributes(a); save.accept(p);
        }

        // 3. PC GVN (Ảnh thật PC gaming case kính cường lực từ Unsplash)
        String[][] pcs = {
            {"PC001","PC GVN Intel i3-12100F / GTX 1650","10900000","https://images.unsplash.com/photo-1587202372775-e229f172b9d7?w=600&h=400&fit=crop","GVN","Intel Core i3-12100F","8GB DDR4 3200MHz","GTX 1650 4GB GDDR6","MSI H610M-A Pro","240GB SATA SSD","Corsair 500W 80+","Xigmatek NYX 3F","Deepcool AG400"},
            {"PC002","PC GVN AMD Ryzen 5 5600G / iGPU","8900000","https://images.unsplash.com/photo-1591488320449-011701bb6704?w=600&h=400&fit=crop","GVN","AMD Ryzen 5 5600G","8GB DDR4 3200MHz","Radeon RX Vega 7","MSI A520M Pro","240GB SATA SSD","Corsair 450W 80+","Xigmatek NYX 3F","Wraith Stealth"},
            {"PC003","PC GVN Intel i5-12400F / RTX 3050","14500000","https://images.unsplash.com/photo-1593640495390-b1c5e53820bb?w=600&h=400&fit=crop","GVN","Intel Core i5-12400F","16GB DDR4 3200MHz","ASUS Dual RTX 3050 8GB","ASUS PRIME B760M-K","500GB NVMe SSD","MSI MAG A550BN 550W","Deepcool CC560","Deepcool AK400"},
            {"PC004","PC GVN Intel i5-13400F / RTX 4060","19900000","https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=600&h=400&fit=crop","GVN","Intel Core i5-13400F","16GB DDR4 3200MHz","ASUS Dual RTX 4060 8GB","MSI MAG B760M Mortar","500GB NVMe SSD","Corsair CV650 650W","Deepcool CC560","Deepcool AK400"},
            {"PC005","PC GVN Intel i5-14400F / RTX 4060 Ti","24500000","https://images.unsplash.com/photo-1555680202-c86f0e12f086?w=600&h=400&fit=crop","GVN","Intel Core i5-14400F","16GB DDR5 4800MHz","GIGABYTE RTX 4060 Ti 8GB","GIGABYTE B760M DS3H","1TB NVMe SSD","Corsair RM650x 650W","Corsair 4000D Airflow","Thermalright PA120"},
            {"PC006","PC GVN AMD Ryzen 5 7500F / RX 6600","16800000","https://images.unsplash.com/photo-1600861194942-f883de0dfe96?w=600&h=400&fit=crop","GVN","AMD Ryzen 5 7500F","16GB DDR5 5600MHz","ASUS Dual RX 6600 8GB","GIGABYTE B650M DS3H","500GB NVMe SSD","Corsair CV600 600W","Deepcool CC560","Deepcool AK400"},
            {"PC007","PC GVN Ryzen 7 7800X3D / RTX 4070","42500000","https://images.unsplash.com/photo-1547394765-185e1e68f34e?w=600&h=400&fit=crop","GVN","AMD Ryzen 7 7800X3D","32GB DDR5 6000MHz","MSI Ventus 3X RTX 4070 12GB","MSI MAG X670E Tomahawk","1TB NVMe SSD","Corsair RM750x 750W","Lian Li Lancool 216","Deepcool LT360 AIO"},
            {"PC008","PC GVN Intel i7-14700F / RTX 4070 Super","52900000","https://images.unsplash.com/photo-1505134485237-a80baa4fb7e9?w=600&h=400&fit=crop","GVN","Intel Core i7-14700F","32GB DDR5 5600MHz","ASUS TUF RTX 4070 Super 12GB","ASUS PRIME Z790-P D4","1TB NVMe SSD","Corsair RM850x 850W","Lian Li Lancool 216","DeepCool LT520"},
            {"PC009","PC GVN Intel i9-14900KF / RTX 4090","98000000","https://images.unsplash.com/photo-1518770660439-4636190af475?w=600&h=400&fit=crop","GVN","Intel Core i9-14900KF","64GB DDR5 6400MHz","ASUS ROG Strix RTX 4090 24GB","ASUS ROG Maximus Z790","2TB NVMe SSD","ASUS ROG Thor 1000W","Lian Li O11 Dynamic","Custom Water Cooling"},
            {"PC010","PC GVN Creator i7-13700 / RTX 3060","28900000","https://images.unsplash.com/photo-1619763501625-67f8a833b7bd?w=600&h=400&fit=crop","GVN","Intel Core i7-13700","32GB DDR4 3600MHz","MSI Ventus 2X RTX 3060 12GB","ASUS PRIME B760M-A","1TB NVMe SSD","Corsair RM650 650W","Deepcool CC560","Deepcool AK620"}
        };
        for (String[] d : pcs) {
            ProductEntity p = new ProductEntity(); p.setProductId(d[0]); p.setRecordType("PRODUCT_INFO"); p.setName(d[1]); p.setPrice(Double.parseDouble(d[2]));
            p.setImageUrl(downloadAndUploadToS3(d[3], d[0]));
            java.util.Map<String,String> a = new java.util.HashMap<>();
            a.put("Hãng","GVN"); a.put("CPU",d[4]); a.put("RAM",d[5]); a.put("VGA",d[6]); a.put("Mainboard",d[7]); a.put("SSD",d[8]); a.put("Nguồn",d[9]); a.put("Case",d[10]); a.put("Tản nhiệt",d[11]); a.put("Bảo hành","36 tháng");
            p.setAttributes(a); save.accept(p);
        }

        // 4. MAIN, CPU, VGA
        String[][] mcvs = {
            {"MCV001","Mainboard ASUS PRIME H610M-K D4","2100000","https://images.unsplash.com/photo-1518770660439-4636190af475?w=600&h=400&fit=crop","Mainboard","ASUS","H610 / LGA1700 / DDR4 / mATX","36 tháng"},
            {"MCV002","Mainboard MSI MAG B760M Mortar DDR4","4200000","https://images.unsplash.com/photo-1555680202-c86f0e12f086?w=600&h=400&fit=crop","Mainboard","MSI","B760 / LGA1700 / DDR4 / mATX","36 tháng"},
            {"MCV003","Mainboard GIGABYTE Z790 AORUS Elite","7900000","https://images.unsplash.com/photo-1562976540-1502c2145851?w=600&h=400&fit=crop","Mainboard","GIGABYTE","Z790 / LGA1700 / DDR5 / ATX","36 tháng"},
            {"MCV004","CPU Intel Core i5-12400F","3400000","https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?w=600&h=400&fit=crop","CPU","Intel","LGA1700 / 6 nhân 12 luồng / 2.5-4.4GHz","36 tháng"},
            {"MCV005","CPU Intel Core i7-14700K","11200000","https://images.unsplash.com/photo-1601524909162-ae8725290836?w=600&h=400&fit=crop","CPU","Intel","LGA1700 / 20 nhân 28 luồng / 3.4-5.6GHz","36 tháng"},
            {"MCV006","CPU AMD Ryzen 5 7600","5600000","https://images.unsplash.com/photo-1595511890410-3b8dc237a537?w=600&h=400&fit=crop","CPU","AMD","AM5 / 6 nhân 12 luồng / 3.8-5.1GHz","36 tháng"},
            {"MCV007","VGA ASUS Dual GeForce RTX 4060 8GB","8500000","https://images.unsplash.com/photo-1587202372634-32705e3bf49c?w=600&h=400&fit=crop","VGA","ASUS","NVIDIA RTX 4060 / 8GB GDDR6 / 128-bit","36 tháng"},
            {"MCV008","VGA MSI Ventus 3X GeForce RTX 4070","17900000","https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?w=600&h=400&fit=crop","VGA","MSI","NVIDIA RTX 4070 / 12GB GDDR6X / 192-bit","36 tháng"},
            {"MCV009","VGA GIGABYTE Radeon RX 6600 Eagle 8GB","6200000","https://images.unsplash.com/photo-1560472354-b33ff0c44a43?w=600&h=400&fit=crop","VGA","GIGABYTE","AMD RX 6600 / 8GB GDDR6 / 128-bit","36 tháng"},
            {"MCV010","VGA ASUS ROG Strix GeForce RTX 4090","59900000","https://images.unsplash.com/photo-1600861194942-f883de0dfe96?w=600&h=400&fit=crop","VGA","ASUS","NVIDIA RTX 4090 / 24GB GDDR6X / 384-bit","36 tháng"}
        };
        for (String[] d : mcvs) {
            ProductEntity p = new ProductEntity(); p.setProductId(d[0]); p.setRecordType("PRODUCT_INFO"); p.setName(d[1]); p.setPrice(Double.parseDouble(d[2]));
            p.setImageUrl(downloadAndUploadToS3(d[3], d[0]));
            java.util.Map<String,String> a = new java.util.HashMap<>();
            a.put("Loại",d[4]); a.put("Hãng",d[5]); a.put("Model/Chipset/GPU",d[6]); a.put("Bảo hành",d[7]);
            p.setAttributes(a); save.accept(p);
        }

        // 5. CASE, NGUỒN, TẢN
        String[][] cnts = {
            {"CNT001","Case Xigmatek NYX 3F RGB","650000","https://images.unsplash.com/photo-1587202372775-e229f172b9d7?w=600&h=400&fit=crop","Case","Xigmatek","Mini Tower / mATX / 3 fan RGB","12 tháng"},
            {"CNT002","Case Deepcool CC560 White","1150000","https://images.unsplash.com/photo-1591488320449-011701bb6704?w=600&h=400&fit=crop","Case","Deepcool","Mid Tower / ATX / 4 fan ARGB","12 tháng"},
            {"CNT003","Case Corsair 4000D Airflow","2450000","https://images.unsplash.com/photo-1593640495390-b1c5e53820bb?w=600&h=400&fit=crop","Case","Corsair","Mid Tower / ATX / Airflow","12 tháng"},
            {"CNT004","Nguồn Corsair CV650 650W","1450000","https://images.unsplash.com/photo-1518770660439-4636190af475?w=600&h=400&fit=crop","Nguồn","Corsair","650W / 80 Plus Bronze","12 tháng"},
            {"CNT005","Nguồn MSI MAG A750GL PCIe 5.0","2550000","https://images.unsplash.com/photo-1562976540-1502c2145851?w=600&h=400&fit=crop","Nguồn","MSI","750W / 80 Plus Gold","12 tháng"},
            {"CNT006","Nguồn ASUS ROG Thor 1000W Platinum II","8900000","https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=600&h=400&fit=crop","Nguồn","ASUS","1000W / 80 Plus Platinum","12 tháng"},
            {"CNT007","Tản nhiệt Deepcool AK400","650000","https://images.unsplash.com/photo-1555680202-c86f0e12f086?w=600&h=400&fit=crop","Tản nhiệt","Deepcool","Tản khí 1 tháp / 4 ống đồng","12 tháng"},
            {"CNT008","Tản nhiệt Thermalright Peerless Assassin","1100000","https://images.unsplash.com/photo-1600861194942-f883de0dfe96?w=600&h=400&fit=crop","Tản nhiệt","Thermalright","Tản khí 2 tháp / 6 ống đồng","12 tháng"},
            {"CNT009","Tản nước AIO Deepcool LT720 360mm","3250000","https://images.unsplash.com/photo-1617654697509-86c41e5cec9b?w=600&h=400&fit=crop","Tản nhiệt","Deepcool","AIO 360mm / 3 fan ARGB","12 tháng"},
            {"CNT010","Tản nước AIO ASUS ROG Ryujin III 360","9800000","https://images.unsplash.com/photo-1547394765-185e1e68f34e?w=600&h=400&fit=crop","Tản nhiệt","ASUS","AIO 360mm / LCD 3.5 inch","12 tháng"}
        };
        for (String[] d : cnts) {
            ProductEntity p = new ProductEntity(); p.setProductId(d[0]); p.setRecordType("PRODUCT_INFO"); p.setName(d[1]); p.setPrice(Double.parseDouble(d[2]));
            p.setImageUrl(downloadAndUploadToS3(d[3], d[0]));
            java.util.Map<String,String> a = new java.util.HashMap<>();
            a.put("Loại",d[4]); a.put("Hãng",d[5]); a.put("Thông số chính",d[6]); a.put("Bảo hành",d[7]);
            p.setAttributes(a); save.accept(p);
        }

        // 6. Ổ CỨNG, RAM, THẺ NHỚ
        String[][] orts = {
            {"ORT001","SSD Kingston NV2 500GB Gen4","950000","https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?w=600&h=400&fit=crop","SSD NVMe","Kingston","500GB","Đọc 3500MB/s / Ghi 2100MB/s"},
            {"ORT002","SSD Samsung 980 Pro 1TB Gen4","2650000","https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?w=600&h=400&fit=crop","SSD NVMe","Samsung","1TB","Đọc 7000MB/s / Ghi 5100MB/s"},
            {"ORT003","SSD WD Blue SN580 1TB Gen4","1850000","https://images.unsplash.com/photo-1553406830-ef2513450d76?w=600&h=400&fit=crop","SSD NVMe","Western Digital","1TB","Đọc 4150MB/s / Ghi 4150MB/s"},
            {"ORT004","RAM Kingston FURY Beast 8GB DDR4","580000","https://images.unsplash.com/photo-1562976540-1502c2145851?w=600&h=400&fit=crop","RAM","Kingston","8GB DDR4","3200MHz / Tản nhôm"},
            {"ORT005","RAM Corsair Vengeance LPX 16GB DDR4","1150000","https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?w=600&h=400&fit=crop","RAM","Corsair","16GB DDR4","3200MHz / Low Profile"},
            {"ORT006","RAM G.Skill Trident Z5 Neo RGB 32GB","3450000","https://images.unsplash.com/photo-1601524909162-ae8725290836?w=600&h=400&fit=crop","RAM","G.Skill","32GB DDR5","6000MHz / AMD EXPO"},
            {"ORT007","RAM ADATA XPG Lancer RGB 16GB DDR5","1650000","https://images.unsplash.com/photo-1595511890410-3b8dc237a537?w=600&h=400&fit=crop","RAM","ADATA","16GB DDR5","6000MHz / RGB"},
            {"ORT008","Thẻ nhớ SanDisk Ultra MicroSDXC 64GB","180000","https://images.unsplash.com/photo-1531079987112-f3af1de35a7c?w=600&h=400&fit=crop","Thẻ nhớ MicroSD","SanDisk","64GB","Đọc 120MB/s / UHS-I"},
            {"ORT009","Thẻ nhớ Samsung EVO Plus MicroSD 128GB","350000","https://images.unsplash.com/photo-1612198188060-c7c2a3b66eae?w=600&h=400&fit=crop","Thẻ nhớ MicroSD","Samsung","128GB","Đọc 130MB/s / UHS-I"},
            {"ORT010","HDD Seagate BarraCuda 2TB 3.5 inch","1550000","https://images.unsplash.com/photo-1544816155-12df9643f363?w=600&h=400&fit=crop","HDD","Seagate","2TB","7200 RPM / SATA 6Gb/s"}
        };
        for (String[] d : orts) {
            ProductEntity p = new ProductEntity(); p.setProductId(d[0]); p.setRecordType("PRODUCT_INFO"); p.setName(d[1]); p.setPrice(Double.parseDouble(d[2]));
            p.setImageUrl(downloadAndUploadToS3(d[3], d[0]));
            java.util.Map<String,String> a = new java.util.HashMap<>();
            a.put("Loại",d[4]); a.put("Hãng",d[5]); a.put("Dung lượng",d[6]); a.put("Thông số tốc độ",d[7]); a.put("Bảo hành","36 tháng");
            p.setAttributes(a); save.accept(p);
        }

        // 7. LOA, MICRO, WEBCAM
        String[][] lmws = {
            {"LMW001","Loa Bluetooth JBL Go 3","950000","https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?w=600&h=400&fit=crop","Loa Bluetooth","JBL","Bluetooth 5.1","4.2W / Pin 5h"},
            {"LMW002","Loa PC Microlab M100 2.0","450000","https://images.unsplash.com/photo-1545454675-3531b543be5d?w=600&h=400&fit=crop","Loa PC","Microlab","Jack 3.5mm","6W RMS"},
            {"LMW003","Loa Soundbar Razer Leviathan V2 X","2850000","https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=600&h=400&fit=crop","Loa Soundbar","Razer","USB-C / Bluetooth","THX Audio / RGB"},
            {"LMW004","Micro Razer Seiren Mini","1250000","https://images.unsplash.com/photo-1593784991095-a205069470b6?w=600&h=400&fit=crop","Microphone","Razer","USB","Cardioid / Siêu nhỏ gọn"},
            {"LMW005","Micro HyperX SoloCast","1650000","https://images.unsplash.com/photo-1625948515691-e1c8cae5b31f?w=600&h=400&fit=crop","Microphone","HyperX","USB","Cardioid / Tap-to-Mute"},
            {"LMW006","Micro Elgato Wave:3","3950000","https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?w=600&h=400&fit=crop","Microphone","Elgato","USB","Cardioid / Wave Link App"},
            {"LMW007","Webcam Rapoo C260 FHD","550000","https://images.unsplash.com/photo-1611532736597-de2d4265fba3?w=600&h=400&fit=crop","Webcam","Rapoo","USB 2.0","1080P 30fps"},
            {"LMW008","Webcam Logitech C922 Pro Stream","2350000","https://images.unsplash.com/photo-1587826080692-f439cd0b70da?w=600&h=400&fit=crop","Webcam","Logitech","USB 2.0","1080P 30fps / AF"},
            {"LMW009","Webcam Razer Kiyo Pro","3850000","https://images.unsplash.com/photo-1574717024743-a3e7b1d8b5b8?w=600&h=400&fit=crop","Webcam","Razer","USB 3.0","1080P 60fps / STARVIS"},
            {"LMW010","Loa Edifier R1700BTS 2.0","3150000","https://images.unsplash.com/photo-1545454675-3531b543be5d?w=600&h=400&fit=crop","Loa Bookshelf","Edifier","Bluetooth / Jack 3.5mm","66W RMS / Thùng gỗ"}
        };
        for (String[] d : lmws) {
            ProductEntity p = new ProductEntity(); p.setProductId(d[0]); p.setRecordType("PRODUCT_INFO"); p.setName(d[1]); p.setPrice(Double.parseDouble(d[2]));
            p.setImageUrl(downloadAndUploadToS3(d[3], d[0]));
            java.util.Map<String,String> a = new java.util.HashMap<>();
            a.put("Loại",d[4]); a.put("Hãng",d[5]); a.put("Kết nối",d[6]); a.put("Thông số chính",d[7]); a.put("Bảo hành","12 tháng");
            p.setAttributes(a); save.accept(p);
        }

        // 8. MÀN HÌNH
        String[][] mons = {
            {"MON001","Màn hình ASUS VZ249HE 24 inch IPS","2650000","https://images.unsplash.com/photo-1527443224154-c4a573d5f5ac?w=600&h=400&fit=crop","ASUS","24 inch","1920x1080 FHD","75Hz","IPS"},
            {"MON002","Màn hình MSI MP243W 24 inch White","2350000","https://images.unsplash.com/photo-1585792180666-f7347c490ee2?w=600&h=400&fit=crop","MSI","23.8 inch","1920x1080 FHD","75Hz","IPS"},
            {"MON003","Màn hình AOC 24G2 144Hz IPS","3850000","https://images.unsplash.com/photo-1616763355548-1b606f439f86?w=600&h=400&fit=crop","AOC","23.8 inch","1920x1080 FHD","144Hz","IPS"},
            {"MON004","Màn hình ViewSonic VX2728 165Hz","4650000","https://images.unsplash.com/photo-1547082299-de196ea013d6?w=600&h=400&fit=crop","ViewSonic","27 inch","1920x1080 FHD","165Hz","IPS"},
            {"MON005","Màn hình Samsung Odyssey G3 27 144Hz","4850000","https://images.unsplash.com/photo-1625315714649-f2b2b3b48f25?w=600&h=400&fit=crop","Samsung","27 inch","1920x1080 FHD","144Hz","VA"},
            {"MON006","Màn hình LG UltraGear 27GR75Q 2K 165Hz","7250000","https://images.unsplash.com/photo-1605464315542-bda3e2f4e605?w=600&h=400&fit=crop","LG","27 inch","2560x1440 QHD","165Hz","IPS"},
            {"MON007","Màn hình ASUS TUF VG279Q1A 165Hz","5250000","https://images.unsplash.com/photo-1499377193864-82682aefed04?w=600&h=400&fit=crop","ASUS","27 inch","1920x1080 FHD","165Hz","IPS"},
            {"MON008","Màn hình Dell UltraSharp U2422H IPS","5850000","https://images.unsplash.com/photo-1551645120-d70bfe84c826?w=600&h=400&fit=crop","Dell","23.8 inch","1920x1080 FHD","60Hz","IPS"},
            {"MON009","Màn hình Gigabyte G24F 2 165Hz IPS","3550000","https://images.unsplash.com/photo-1619953942547-233ac5d748b9?w=600&h=400&fit=crop","Gigabyte","23.8 inch","1920x1080 FHD","165Hz","IPS"},
            {"MON010","Màn hình Samsung Odyssey G9 49 inch","27900000","https://images.unsplash.com/photo-1593305841991-05c297ba4575?w=600&h=400&fit=crop","Samsung","49 inch","5120x1440 DQHD","240Hz","VA Cong"}
        };
        for (String[] d : mons) {
            ProductEntity p = new ProductEntity(); p.setProductId(d[0]); p.setRecordType("PRODUCT_INFO"); p.setName(d[1]); p.setPrice(Double.parseDouble(d[2]));
            p.setImageUrl(downloadAndUploadToS3(d[3], d[0]));
            java.util.Map<String,String> a = new java.util.HashMap<>();
            a.put("Hãng",d[4]); a.put("Kích thước",d[5]); a.put("Độ phân giải",d[6]); a.put("Tần số quét",d[7]); a.put("Tấm nền",d[8]); a.put("Bảo hành","24 tháng");
            p.setAttributes(a); save.accept(p);
        }

        // 9. BÀN PHÍM
        String[][] keys = {
            {"KEY001","Bàn phím cơ DareU EK87 TKL RGB","590000","https://images.unsplash.com/photo-1595044426077-d36d9236d44a?w=600&h=400&fit=crop","DareU","Phím cơ TKL","DareU Blue Switch","USB có dây"},
            {"KEY002","Bàn phím giả cơ Logitech G213 Prodigy","1150000","https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=600&h=400&fit=crop","Logitech","Phím giả cơ Full-size","Logitech Romer-G","USB có dây"},
            {"KEY003","Bàn phím cơ Akko 3087 v2 Horizon","1350000","https://images.unsplash.com/photo-1582967788606-a171c1080cb0?w=600&h=400&fit=crop","Akko","Phím cơ TKL (87 phím)","Akko V2 Cream Yellow","USB có dây"},
            {"KEY004","Bàn phím cơ Corsair K63 Wireless","2450000","https://images.unsplash.com/photo-1618384887929-16ec33fab9ef?w=600&h=400&fit=crop","Corsair","Phím cơ TKL","Cherry MX Red","USB Wireless"},
            {"KEY005","Bàn phím cơ Razer BlackWidow V4 X","3250000","https://images.unsplash.com/photo-1561112078-7d24e04c3407?w=600&h=400&fit=crop","Razer","Phím cơ Full-size","Razer Yellow","USB có dây"},
            {"KEY006","Bàn phím cơ Logitech G Pro X TKL","4550000","https://images.unsplash.com/photo-1541140532154-b024d705b90a?w=600&h=400&fit=crop","Logitech","Phím cơ TKL","GX Blue Switch","USB Wireless"},
            {"KEY007","Bàn phím văn phòng Logitech K120","180000","https://images.unsplash.com/photo-1515378960530-7c0da6231fb1?w=600&h=400&fit=crop","Logitech","Phím màng Full-size","Dome Switch","USB có dây"},
            {"KEY008","Bàn phím cơ không dây Rapoo V500 Pro","750000","https://images.unsplash.com/photo-1600490036275-53267a8b9440?w=600&h=400&fit=crop","Rapoo","Phím cơ Full-size","Rapoo Red Switch","USB Wireless 2.4GHz"},
            {"KEY009","Bàn phím cơ ASUS ROG Strix Scope II","2850000","https://images.unsplash.com/photo-1655195595547-f8cb0b5e6cde?w=600&h=400&fit=crop","ASUS","Phím cơ Full-size","ROG RX Red","USB có dây"},
            {"KEY010","Bàn phím cơ Leopold FC750R PD White","3150000","https://images.unsplash.com/photo-1547082299-de196ea013d6?w=600&h=400&fit=crop","Leopold","Phím cơ TKL","Cherry MX Brown","USB có dây"}
        };
        for (String[] d : keys) {
            ProductEntity p = new ProductEntity(); p.setProductId(d[0]); p.setRecordType("PRODUCT_INFO"); p.setName(d[1]); p.setPrice(Double.parseDouble(d[2]));
            p.setImageUrl(downloadAndUploadToS3(d[3], d[0]));
            java.util.Map<String,String> a = new java.util.HashMap<>();
            a.put("Hãng",d[4]); a.put("Loại phím",d[5]); a.put("Switch",d[6]); a.put("Kết nối",d[7]); a.put("Bảo hành","24 tháng");
            p.setAttributes(a); save.accept(p);
        }

        // 10. CHUỘT + LÓT CHUỘT
        String[][] msls = {
            {"MSL001","Chuột gaming DareU EM908 RGB","350000","https://images.unsplash.com/photo-1527814050087-3793815479db?w=600&h=400&fit=crop","DareU","6400 DPI","USB có dây"},
            {"MSL002","Chuột gaming Logitech G102 Lightsync","420000","https://images.unsplash.com/photo-1563297007-0686b7003af7?w=600&h=400&fit=crop","Logitech","8000 DPI","USB có dây"},
            {"MSL003","Chuột gaming Razer DeathAdder Essential","450000","https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?w=600&h=400&fit=crop","Razer","6400 DPI","USB có dây"},
            {"MSL004","Chuột không dây Logitech G304","850000","https://images.unsplash.com/photo-1586953208448-b95a79798f07?w=600&h=400&fit=crop","Logitech","12000 DPI","USB Wireless"},
            {"MSL005","Chuột gaming HyperX Pulsefire Haste","990000","https://images.unsplash.com/photo-1605721911519-3dfeb3be25e7?w=600&h=400&fit=crop","HyperX","16000 DPI","USB có dây"},
            {"MSL006","Chuột không dây Razer Basilisk V3 Pro","3650000","https://images.unsplash.com/photo-1598440947619-2c35fc9aa908?w=600&h=400&fit=crop","Razer","30000 DPI","USB Wireless / BT"},
            {"MSL007","Chuột không dây Logitech G Pro X Superlight","3850000","https://images.unsplash.com/photo-1629429408209-1f912961dbd5?w=600&h=400&fit=crop","Logitech","32000 DPI","USB Wireless"},
            {"MSL008","Chuột văn phòng Logitech M220 Silent","250000","https://images.unsplash.com/photo-1460925895917-afdab827c52f?w=600&h=400&fit=crop","Logitech","1000 DPI","USB Wireless Nano"},
            {"MSL009","Lót chuột SteelSeries QCK Mini","180000","https://images.unsplash.com/photo-1616763355548-1b606f439f86?w=600&h=400&fit=crop","SteelSeries","Không áp dụng","Không áp dụng"},
            {"MSL010","Lót chuột Razer Goliathus Extended","1150000","https://images.unsplash.com/photo-1612198188060-c7c2a3b66eae?w=600&h=400&fit=crop","Razer","Không áp dụng","USB có dây (LED RGB)"}
        };
        for (String[] d : msls) {
            ProductEntity p = new ProductEntity(); p.setProductId(d[0]); p.setRecordType("PRODUCT_INFO"); p.setName(d[1]); p.setPrice(Double.parseDouble(d[2]));
            p.setImageUrl(downloadAndUploadToS3(d[3], d[0]));
            java.util.Map<String,String> a = new java.util.HashMap<>();
            a.put("Hãng",d[4]); a.put("DPI",d[5]); a.put("Kết nối",d[6]); a.put("Bảo hành","24 tháng");
            p.setAttributes(a); save.accept(p);
        }

        // 11. TAI NGHE
        String[][] hdps = {
            {"HDP001","Tai nghe gaming DareU EH416 7.1 RGB","380000","https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&h=400&fit=crop","DareU","Over-ear / Giả lập 7.1","USB có dây"},
            {"HDP002","Tai nghe gaming Logitech G335 Wired","1250000","https://images.unsplash.com/photo-1583394838336-acd977736f90?w=600&h=400&fit=crop","Logitech","Over-ear / Stereo","Jack 3.5mm"},
            {"HDP003","Tai nghe gaming Razer BlackShark V2 X","1150000","https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?w=600&h=400&fit=crop","Razer","Over-ear / 7.1","Jack 3.5mm"},
            {"HDP004","Tai nghe không dây Logitech G435","1650000","https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=600&h=400&fit=crop","Logitech","Over-ear","Wireless / Bluetooth"},
            {"HDP005","Tai nghe gaming HyperX Cloud II","1950000","https://images.unsplash.com/photo-1599669454699-248893623440?w=600&h=400&fit=crop","HyperX","Over-ear / 7.1","USB / Jack 3.5mm"},
            {"HDP006","Tai nghe không dây Corsair HS80 RGB","3650000","https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=600&h=400&fit=crop","Corsair","Over-ear / Dolby","USB Wireless"},
            {"HDP007","Tai nghe không dây Razer BlackShark V2 Pro","4250000","https://images.unsplash.com/photo-1484704849700-f032a568e944?w=600&h=400&fit=crop","Razer","Over-ear","Wireless / Bluetooth"},
            {"HDP008","Tai nghe không dây Logitech G Pro X 2","5450000","https://images.unsplash.com/photo-1625245488600-ff4a3db02db6?w=600&h=400&fit=crop","Logitech","Over-ear","Wireless / Bluetooth"},
            {"HDP009","Tai nghe In-ear JBL Quantum 50","390000","https://images.unsplash.com/photo-1572536147248-ac59a8abfa4b?w=600&h=400&fit=crop","JBL","In-ear","Jack 3.5mm"},
            {"HDP010","Tai nghe In-ear ASUS ROG Cetra II Core","990000","https://images.unsplash.com/photo-1615655096345-61a54750068d?w=600&h=400&fit=crop","ASUS","In-ear / ANC","Jack 3.5mm"}
        };
        for (String[] d : hdps) {
            ProductEntity p = new ProductEntity(); p.setProductId(d[0]); p.setRecordType("PRODUCT_INFO"); p.setName(d[1]); p.setPrice(Double.parseDouble(d[2]));
            p.setImageUrl(downloadAndUploadToS3(d[3], d[0]));
            java.util.Map<String,String> a = new java.util.HashMap<>();
            a.put("Hãng",d[4]); a.put("Loại",d[5]); a.put("Kết nối",d[6]); a.put("Bảo hành","12 tháng");
            p.setAttributes(a); save.accept(p);
        }

        System.out.println("✅ Seed hoàn tất! Đã dọn S3 bucket, database và seed thành công 110 sản phẩm mẫu!");
    }
}
