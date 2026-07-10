package com.gearstore.backend;

import com.gearstore.backend.entity.ProductEntity;
import com.gearstore.backend.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
public class DataInitializerTest {

    @Autowired
    private ProductRepository productRepository;

    @Value("${aws.dynamodb.accesskey}")
    private String accessKey;

    @Value("${aws.dynamodb.secretkey}")
    private String secretKey;

    @Value("${aws.dynamodb.region}")
    private String region;

    private static final String BUCKET_NAME = "gearstore-data-images";

    @Test
    public void pushDataToAWS() {
        System.out.println("=== BAT DAU UPLOAD ANH LEN S3 & LUU VAO DYNAMODB ===");

        // Khởi tạo S3Client bằng key đã có trong application.properties
        S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .build();

        // Đường dẫn tương đối đến các file ảnh tĩnh trong project
        Map<String, String> localImages = new HashMap<>();
        localImages.put("LAP003", "src/main/resources/static/images/LAP003.png");
        localImages.put("PC001", "src/main/resources/static/images/PC001.png");
        localImages.put("KB001", "src/main/resources/static/images/KB001.png");
        localImages.put("MS001", "src/main/resources/static/images/MS001.png");
        localImages.put("MON001", "src/main/resources/static/images/MON001.png");

        Map<String, ProductEntity> products = new HashMap<>();

        // 1. LAP003: ASUS ROG Strix G16
        ProductEntity lap003 = new ProductEntity();
        lap003.setProductId("LAP003");
        lap003.setRecordType("PRODUCT_INFO");
        lap003.setName("Laptop Gaming ASUS ROG Strix G16 G614JV");
        lap003.setPrice(34990000.0);
        Map<String, String> lapSpecs = new HashMap<>();
        lapSpecs.put("CPU", "Intel Core i7 13650HX (Up to 4.9 GHz, 14 cores, 20 threads)");
        lapSpecs.put("Ram", "16GB DDR5 4800MHz (2x8GB, hỗ trợ nâng cấp)");
        lapSpecs.put("VGA", "NVIDIA GeForce RTX 4060 8GB GDDR6");
        lapSpecs.put("Màn hình", "16 inch WUXGA 165Hz IPS");
        lap003.setAttributes(lapSpecs);
        products.put("LAP003", lap003);

        // 2. PC001: PC GVN Intel i5 / RTX 4060
        ProductEntity pc001 = new ProductEntity();
        pc001.setProductId("PC001");
        pc001.setRecordType("PRODUCT_INFO");
        pc001.setName("PC GVN Intel Core i5 / RTX 4060");
        pc001.setPrice(21500000.0);
        Map<String, String> pcSpecs = new HashMap<>();
        pcSpecs.put("CPU", "Intel Core i5 13400F (Up to 4.6 GHz, 10 cores, 16 threads)");
        pcSpecs.put("Ram", "16GB DDR4 3200MHz (Dual Channel)");
        pcSpecs.put("VGA", "GeForce RTX 4060 8GB GDDR6");
        pcSpecs.put("Nguồn", "650W 80 Plus Bronze");
        pc001.setAttributes(pcSpecs);
        products.put("PC001", pc001);

        // 3. KB001: Akko mechanical keyboard
        ProductEntity kb001 = new ProductEntity();
        kb001.setProductId("KB001");
        kb001.setRecordType("PRODUCT_INFO");
        kb001.setName("Bàn phím cơ AKKO 3098B Multi-Modes Retro");
        kb001.setPrice(2390000.0);
        Map<String, String> kbSpecs = new HashMap<>();
        kbSpecs.put("Switch", "Akko CS Jelly White (Linear)");
        kbSpecs.put("Kết nối", "Bluetooth 5.0 / Wireless 2.4Ghz / Type-C");
        kbSpecs.put("Layout", "98 phím (gọn nhẹ có Numpad)");
        kbSpecs.put("Keycap", "PBT Double-Shot, OEM profile");
        kb001.setAttributes(kbSpecs);
        products.put("KB001", kb001);

        // 4. MS001: Logitech G502
        ProductEntity ms001 = new ProductEntity();
        ms001.setProductId("MS001");
        ms001.setRecordType("PRODUCT_INFO");
        ms001.setName("Chuột Gaming Logitech G502 Hero High Performance");
        ms001.setPrice(1090000.0);
        Map<String, String> msSpecs = new HashMap<>();
        msSpecs.put("DPI", "100 - 25,600 DPI");
        msSpecs.put("Mắt đọc", "HERO 25K chuyên nghiệp");
        msSpecs.put("Nút bấm", "11 nút lập trình được qua G Hub");
        msSpecs.put("Trọng lượng", "121g (có kèm tạ điều chỉnh)");
        ms001.setAttributes(msSpecs);
        products.put("MS001", ms001);

        // 5. MON001: ASUS TUF Monitor
        ProductEntity mon001 = new ProductEntity();
        mon001.setProductId("MON001");
        mon001.setRecordType("PRODUCT_INFO");
        mon001.setName("Màn hình ASUS TUF Gaming VG279Q3A 27\" Fast IPS 180Hz");
        mon001.setPrice(4890000.0);
        Map<String, String> monSpecs = new HashMap<>();
        monSpecs.put("Kích thước", "27 inch Flat");
        monSpecs.put("Tấm nền", "Fast IPS (Màu sắc chuẩn, góc nhìn rộng)");
        monSpecs.put("Tần số quét", "180Hz (Phản hồi siêu tốc 1ms)");
        monSpecs.put("Độ phân giải", "Full HD (1920 x 1080)");
        mon001.setAttributes(monSpecs);
        products.put("MON001", mon001);

        // Tiến hành upload S3 và lưu DynamoDB
        for (String id : localImages.keySet()) {
            String imagePath = localImages.get(id);
            String s3Key = id + ".png";
            File file = new File(imagePath);

            if (!file.exists()) {
                System.out.println("❌ Không tìm thấy file ảnh: " + imagePath);
                continue;
            }

            try {
                System.out.println("📤 Đang upload file " + s3Key + " lên S3 bucket: " + BUCKET_NAME);
                
                // Thử upload có ACL public read, nếu bucket chặn ACL thì fallback upload thường
                try {
                    s3Client.putObject(
                            PutObjectRequest.builder()
                                    .bucket(BUCKET_NAME)
                                    .key(s3Key)
                                    .contentType("image/png")
                                    .acl(ObjectCannedACL.PUBLIC_READ)
                                    .build(),
                            Paths.get(imagePath)
                    );
                    System.out.println("👉 Đã upload thành công với ACL public-read.");
                } catch (Exception e) {
                    System.out.println("⚠️ Bucket không hỗ trợ ACL hoặc bị chặn. Đang upload mặc định...");
                    s3Client.putObject(
                            PutObjectRequest.builder()
                                    .bucket(BUCKET_NAME)
                                    .key(s3Key)
                                    .contentType("image/png")
                                    .build(),
                            Paths.get(imagePath)
                    );
                    System.out.println("👉 Đã upload thành công không dùng ACL.");
                }

                // Tạo S3 URL của ảnh
                String s3Url = "https://" + BUCKET_NAME + ".s3." + region + ".amazonaws.com/" + s3Key;
                System.out.println("🔗 S3 Image URL: " + s3Url);

                // Gán link ảnh S3 vào sản phẩm và lưu lên DynamoDB
                ProductEntity product = products.get(id);
                product.setImageUrl(s3Url);
                
                System.out.println("💾 Đang lưu " + id + " vào DynamoDB...");
                productRepository.saveProduct(product);
                System.out.println("🎉 Lưu thành công " + id + " lên DynamoDB!");

            } catch (Exception e) {
                System.out.println("❌ Lỗi khi xử lý sản phẩm " + id + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("=== HOÀN THÀNH TẤT CẢ UPLOAD S3 & DYNAMODB ===");
    }
}
