package dev.peluci.benchos.domain.entities;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

public class Sale extends BaseEntity {

    private Product product;
    private Customer customer;
    private Integer quantity;
    private BigDecimal totalPrice;
    private Timestamp soldAt;

    public Sale(Product product, Customer customer, Integer quantity, BigDecimal totalPrice, Timestamp soldAt) {
        super();
        this.product = product;
        this.customer = customer;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.soldAt = soldAt;
    }

    public Sale(UUID id, Boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                Product product, Customer customer, Integer quantity, BigDecimal totalPrice, Timestamp soldAt) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.product = product;
        this.customer = customer;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.soldAt = soldAt;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Timestamp getSoldAt() {
        return soldAt;
    }

    public void setSoldAt(Timestamp soldAt) {
        this.soldAt = soldAt;
    }
}
