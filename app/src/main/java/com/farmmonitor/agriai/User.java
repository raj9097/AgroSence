package com.farmmonitor.agriai;

public class User {

    private int id;
    private String name;
    private String email;
    private String city;
    private String password;

    // ✅ Default (no-argument) constructor — required for database or session
    public User() {
    }

    // ✅ Parameterized constructor — useful when creating a new user
    public User(int id, String name, String email, String city, String password) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.city = city;
        this.password = password;
    }

    // ✅ Alternate constructor (if ID not yet assigned)
    public User(String name, String email, String city, String password) {
        this.name = name;
        this.email = email;
        this.city = city;
        this.password = password;
    }

    // ✅ Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // ✅ Optional: for debugging or logging
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", city='" + city + '\'' +
                '}';
    }
}
