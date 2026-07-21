package com.gearstore.backend.entity;

public class UserEntity {
    private String username;
    private String password;
    private String fullName;
    private String role; // ADMIN hoặc USER
    private String email;
    private String phone;
    private String address;

    public UserEntity() {}

    // Constructor 5 tham số nạp chồng (Overload) để giữ tính tương thích với file Test
    public UserEntity(String username, String password, String fullName, String role, String email) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.email = email;
        this.phone = "";
        this.address = "";
    }

    // Constructor đầy đủ 7 tham số
    public UserEntity(String username, String password, String fullName, String role, String email, String phone, String address) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.email = email;
        this.phone = phone;
        this.address = address;
    }

    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
