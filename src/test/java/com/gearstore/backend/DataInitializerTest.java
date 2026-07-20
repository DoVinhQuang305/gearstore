package com.gearstore.backend;

import com.gearstore.backend.entity.ProductEntity;
import com.gearstore.backend.entity.UserEntity;
import com.gearstore.backend.repository.ProductRepository;
import com.gearstore.backend.repository.UserRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class DataInitializerTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${aws.dynamodb.accesskey}")
    private String accessKey;

    @Value("${aws.dynamodb.secretkey}")
    private String secretKey;

    @Value("${aws.dynamodb.region}")
    private String region;

    private static final String BUCKET_NAME = "gearstore-data-images";

    @Test
    public void pushDataToAWS() {
        System.out.println("=== BAT DAU UPLOAD ANH LEN S3 & LUU TOAN BO DULIEU VAO DYNAMODB ===");

        // Khởi tạo bảng Users và lưu tài khoản mẫu
        userRepository.createTableIfNotExists();
        System.out.println("⏳ Đang chờ bảng GearStore_Users chuyển sang ACTIVE...");
        
        boolean saved = false;
        for (int i = 0; i < 6; i++) { // Thử tối đa 6 lần (30 giây)
            try {
                userRepository.saveUser(new UserEntity("admin", "admin123", "Quản trị viên GearStore", "ADMIN", "admin@gearstore.vn"));
                userRepository.saveUser(new UserEntity("user", "user123", "Nguyễn Văn A", "USER", "user@gmail.com"));
                saved = true;
                break;
            } catch (Exception e) {
                System.out.println("⏳ Bảng đang khởi tạo, chờ 5 giây rồi thử lại...");
                try { Thread.sleep(5000); } catch (InterruptedException ie) {}
            }
        }
        if (saved) {
            System.out.println("✅ Đã khởi tạo dữ liệu tài khoản Admin & User thành công!");
        } else {
            System.out.println("❌ Không thể khởi tạo tài khoản mẫu.");
        }

        S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .build();

        // 1. Upload 5 file ảnh gốc lên S3 và lấy URL tương ứng
        Map<String, String> localImages = new HashMap<>();
        localImages.put("LAP003", "src/main/resources/static/images/LAP003.png");
        localImages.put("PC001", "src/main/resources/static/images/PC001.png");
        localImages.put("KB001", "src/main/resources/static/images/KB001.png");
        localImages.put("MS001", "src/main/resources/static/images/MS001.png");
        localImages.put("MON001", "src/main/resources/static/images/MON001.png");

        Map<String, String> uploadedUrls = new HashMap<>();

        for (String id : localImages.keySet()) {
            String imagePath = localImages.get(id);
            String s3Key = id + ".png";
            File file = new File(imagePath);

            if (!file.exists()) {
                System.out.println("❌ Không tìm thấy file ảnh local: " + imagePath);
                continue;
            }

            try {
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
                } catch (Exception e) {
                    s3Client.putObject(
                            PutObjectRequest.builder()
                                    .bucket(BUCKET_NAME)
                                    .key(s3Key)
                                    .contentType("image/png")
                                    .build(),
                            Paths.get(imagePath)
                    );
                }
                String s3Url = "https://" + BUCKET_NAME + ".s3." + region + ".amazonaws.com/" + s3Key;
                uploadedUrls.put(id, s3Url);
                System.out.println("📤 Uploaded S3: " + id + " -> " + s3Url);
            } catch (Exception e) {
                System.out.println("❌ Lỗi upload ảnh " + id + ": " + e.getMessage());
            }
        }

        // 2. Khai báo danh sách 15 sản phẩm đa dạng mẫu mã và phân khúc
        List<ProductEntity> list = new ArrayList<>();

        // LAPTOPS (ASUS, ACER, MSI, LENOVO)
        list.add(createProduct("LAP001", "Laptop ASUS Zenbook 14 OLED UX3402", 19990000.0, uploadedUrls.get("LAP003"),
                Map.of("Hãng", "ASUS", "CPU", "Intel Core i5 1240P", "Ram", "16GB LPDDR5", "VGA", "Intel Iris Xe Graphics", "Màn hình", "14 inch 2.8K OLED")));
        
        list.add(createProduct("LAP002", "Laptop Acer Aspire 3 A315", 11500000.0, uploadedUrls.get("LAP003"),
                Map.of("Hãng", "Acer", "CPU", "Intel Core i3 1215U", "Ram", "8GB DDR4", "VGA", "Intel UHD Graphics", "Màn hình", "15.6 inch Full HD")));
        
        list.add(createProduct("LAP003", "Laptop Gaming ASUS ROG Strix G16 G614JV", 34990000.0, uploadedUrls.get("LAP003"),
                Map.of("Hãng", "ASUS", "CPU", "Intel Core i7 13650HX", "Ram", "16GB DDR5", "VGA", "NVIDIA GeForce RTX 4060", "Màn hình", "16 inch 165Hz")));

        list.add(createProduct("LAP004", "Laptop Lenovo ThinkBook 14 G6", 16800000.0, uploadedUrls.get("LAP003"),
                Map.of("Hãng", "Lenovo", "CPU", "Intel Core i5 1335U", "Ram", "16GB DDR5", "VGA", "Intel Iris Xe Graphics", "Màn hình", "14 inch Full HD")));

        list.add(createProduct("LAP005", "Laptop MSI Modern 15 B7M", 13990000.0, uploadedUrls.get("LAP003"),
                Map.of("Hãng", "MSI", "CPU", "AMD Ryzen 5 7530U", "Ram", "8GB DDR4", "VGA", "AMD Radeon Graphics", "Màn hình", "15.6 inch Full HD")));

        // PC GVN
        list.add(createProduct("PC001", "PC GVN Intel Core i5 / RTX 4060", 21500000.0, uploadedUrls.get("PC001"),
                Map.of("Hãng", "GVN", "CPU", "Intel Core i5 13400F", "Ram", "16GB DDR4", "VGA", "GeForce RTX 4060", "Nguồn", "650W 80 Plus")));

        list.add(createProduct("PC002", "PC GVN Student Core i3", 9990000.0, uploadedUrls.get("PC001"),
                Map.of("Hãng", "GVN", "CPU", "Intel Core i3 12100F", "Ram", "8GB DDR4", "VGA", "Intel UHD Graphics", "Nguồn", "450W")));

        list.add(createProduct("PC003", "PC GVN VIP Core i7 / RTX 4070", 42500000.0, uploadedUrls.get("PC001"),
                Map.of("Hãng", "GVN", "CPU", "Intel Core i7 14700F", "Ram", "32GB DDR5", "VGA", "GeForce RTX 4070", "Nguồn", "750W Gold")));

        // KEYBOARDS
        list.add(createProduct("KB001", "Bàn phím cơ AKKO 3098B Multi-Modes Retro", 2390000.0, uploadedUrls.get("KB001"),
                Map.of("Hãng", "AKKO", "Switch", "Akko CS Jelly White", "Kết kết nối", "Wireless / Bluetooth", "Layout", "98 phím")));

        list.add(createProduct("KB002", "Bàn phím cơ Corsair K70 RGB MK.2", 3890000.0, uploadedUrls.get("KB001"),
                Map.of("Hãng", "Corsair", "Switch", "Cherry MX Red", "Kết kết nối", "Wired USB", "Layout", "Full size")));

        // MICE
        list.add(createProduct("MS001", "Chuột Gaming Logitech G502 Hero High Performance", 1090000.0, uploadedUrls.get("MS001"),
                Map.of("Hãng", "Logitech", "DPI", "25,600 DPI", "Mắt đọc", "HERO 25K", "Nút bấm", "11 nút lập trình")));

        list.add(createProduct("MS002", "Chuột Gaming Razer DeathAdder V3", 1890000.0, uploadedUrls.get("MS001"),
                Map.of("Hãng", "Razer", "DPI", "30,000 DPI", "Mắt đọc", "Focus Pro 30K", "Trọng lượng", "59g")));

        // MONITORS
        list.add(createProduct("MON001", "Màn hình ASUS TUF Gaming VG279Q3A 27\" Fast IPS 180Hz", 4890000.0, uploadedUrls.get("MON001"),
                Map.of("Hãng", "ASUS", "Kích thước", "27 inch", "Tần số quét", "180Hz", "Độ phân giải", "Full HD (1920x1080)")));

        list.add(createProduct("MON002", "Màn hình Dell UltraSharp U2424H 24\" IPS 120Hz", 6490000.0, uploadedUrls.get("MON001"),
                Map.of("Hãng", "Dell", "Kích thước", "24 inch", "Tần số quét", "120Hz", "Độ phân giải", "Full HD (1920x1080)")));

        // HEADPHONE
        list.add(createProduct("EP001", "Tai nghe Gaming Kingston HyperX Cloud II", 1950000.0, uploadedUrls.get("MS001"),
                Map.of("Hãng", "HyperX", "Kết nối", "Wired Jack 3.5mm / USB soundcard", "Âm thanh", "Giả lập 7.1 Surround Sound")));

        // 3. Tiến hành lưu tất cả vào DynamoDB
        for (ProductEntity p : list) {
            try {
                System.out.println("💾 Đang lưu: " + p.getProductId() + " - " + p.getName());
                productRepository.saveProduct(p);
            } catch (Exception e) {
                System.out.println("❌ Lỗi khi lưu sản phẩm " + p.getProductId() + ": " + e.getMessage());
            }
        }
        System.out.println("🎉 HOÀN THÀNH TẤT CẢ LƯU DỮ LIỆU DYNAMODB!");
    }

    private ProductEntity createProduct(String id, String name, double price, String imgUrl, Map<String, String> specs) {
        ProductEntity p = new ProductEntity();
        p.setProductId(id);
        p.setRecordType("PRODUCT_INFO");
        p.setName(name);
        p.setPrice(price);
        p.setImageUrl(imgUrl);
        p.setAttributes(specs);
        return p;
    }
}
