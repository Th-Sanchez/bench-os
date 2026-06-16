package dev.peluci.benchos.domain.entities;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

public class ServiceOrder extends BaseEntity {

    private Customer customer;
    private User user;
    private String status;
    private String deviceModel;
    private String deviceVoltage;
    private Date receivedAt;
    private Date deadlineAt;
    private Boolean discardAuthorized;
    private String notes;

    public ServiceOrder(Customer customer, User user, String status, String deviceModel, String deviceVoltage,
                        Date receivedAt, Date deadlineAt, Boolean discardAuthorized, String notes) {
        super();
        this.customer = customer;
        this.user = user;
        this.status = status;
        this.deviceModel = deviceModel;
        this.deviceVoltage = deviceVoltage;
        this.receivedAt = receivedAt;
        this.deadlineAt = deadlineAt;
        this.discardAuthorized = discardAuthorized;
        this.notes = notes;
    }

    public ServiceOrder(UUID id, Boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                        Customer customer, User user, String status, String deviceModel, String deviceVoltage,
                        Date receivedAt, Date deadlineAt, Boolean discardAuthorized, String notes) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.customer = customer;
        this.user = user;
        this.status = status;
        this.deviceModel = deviceModel;
        this.deviceVoltage = deviceVoltage;
        this.receivedAt = receivedAt;
        this.deadlineAt = deadlineAt;
        this.discardAuthorized = discardAuthorized;
        this.notes = notes;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getDeviceVoltage() {
        return deviceVoltage;
    }

    public void setDeviceVoltage(String deviceVoltage) {
        this.deviceVoltage = deviceVoltage;
    }

    public Date getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Date receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Date getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(Date deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public Boolean getDiscardAuthorized() {
        return discardAuthorized;
    }

    public void setDiscardAuthorized(Boolean discardAuthorized) {
        this.discardAuthorized = discardAuthorized;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
