package com.gearstore.backend.controller;

import com.gearstore.backend.entity.UserEntity;
import com.gearstore.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // API Đăng nhập: POST http://localhost:8080/auth/login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("Vui lòng cung cấp tên đăng nhập và mật khẩu!");
        }

        UserEntity user = userRepository.getUser(username);
        if (user == null || !user.getPassword().equals(password)) {
            return ResponseEntity.status(401).body("Tài khoản hoặc mật khẩu không chính xác!");
        }

        // Đăng nhập thành công, trả về thông tin tài khoản (ẩn mật khẩu)
        return ResponseEntity.ok(Map.of(
            "username", user.getUsername(),
            "fullName", user.getFullName(),
            "role", user.getRole(),
            "email", user.getEmail()
        ));
    }

    // API Đăng ký: POST http://localhost:8080/auth/register
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserEntity newUser) {
        if (newUser.getUsername() == null || newUser.getPassword() == null) {
            return ResponseEntity.badRequest().body("Vui lòng nhập tên tài khoản và mật khẩu!");
        }

        UserEntity existing = userRepository.getUser(newUser.getUsername());
        if (existing != null) {
            return ResponseEntity.badRequest().body("Tên đăng nhập đã tồn tại trên hệ thống!");
        }

        // Mặc định đăng ký mới là quyền USER thường
        newUser.setRole("USER");
        userRepository.saveUser(newUser);

        return ResponseEntity.ok("Đăng ký tài khoản thành công!");
    }
}
