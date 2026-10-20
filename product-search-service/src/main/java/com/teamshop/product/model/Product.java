package com.teamshop.product.model;

import java.io.Serializable;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

public class Product implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id private String id;
    private String name;
    private String description;
    @Indexed
    private String category;
    private String brand;
    private double price;
    private int stock;

    public Product() {}
    public Product(String id, String name, String description, String category, String brand, double price, int stock) {
        this.id = id; this.name = name; this.description = description; this.category = category;
        this.brand = brand; this.price = price; this.stock = stock;
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
}
