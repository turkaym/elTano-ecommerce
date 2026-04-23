package com.eltano.ecommerce.catalog.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "products", uniqueConstraints = {
        @UniqueConstraint(name = "uk_products_slug", columnNames = "slug")
})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 180)
    private String slug;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType productType = ProductType.ENVASADO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryPolicy inventoryPolicy = InventoryPolicy.PER_VARIANT;

    @Column
    private Integer stockBaseGrams;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public ProductType getProductType() {
        return productType == null ? ProductType.ENVASADO : productType;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    public InventoryPolicy getInventoryPolicy() {
        return inventoryPolicy == null ? InventoryPolicy.PER_VARIANT : inventoryPolicy;
    }

    public void setInventoryPolicy(InventoryPolicy inventoryPolicy) {
        this.inventoryPolicy = inventoryPolicy;
    }

    public Integer getStockBaseGrams() {
        return stockBaseGrams;
    }

    public void setStockBaseGrams(Integer stockBaseGrams) {
        this.stockBaseGrams = stockBaseGrams;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public List<ProductVariant> getVariants() {
        return variants;
    }

    public void replaceVariants(List<ProductVariant> newVariants) {
        variants.clear();
        for (ProductVariant variant : newVariants) {
            addVariant(variant);
        }
    }

    public void addVariant(ProductVariant variant) {
        variant.setProduct(this);
        variants.add(variant);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
