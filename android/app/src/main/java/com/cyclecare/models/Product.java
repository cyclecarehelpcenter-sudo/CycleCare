package com.cyclecare.models;

import com.google.gson.annotations.SerializedName;

public class Product {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("price")
    private double price;

    @SerializedName("discount_price")
    private Double discountPrice;

    @SerializedName("stock")
    private int stock;

    @SerializedName("sku")
    private String sku;

    @SerializedName("category")
    private String category;

    public Product() {}

    public Product(String id, String name, String description, double price, String category, int stock) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.stock = stock;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public Double getDiscountPrice() { return discountPrice; }
    public double getActivePrice() { return discountPrice != null ? discountPrice : price; }
    public int getStock() { return stock; }
    public String getSku() { return sku; }
    public String getCategory() { return category != null ? category : "Period Care"; }
}
