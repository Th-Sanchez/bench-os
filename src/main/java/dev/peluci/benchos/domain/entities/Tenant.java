package dev.peluci.benchos.domain.entities;

import java.time.LocalDateTime;
import java.util.UUID;

public class Tenant extends BaseEntity {

    private String tradeName;
    private String companyName;
    private String cnpj;
    private String phone;
    private String street;
    private String number;
    private String city;
    private String state;
    private String zipCode;

    public Tenant(String tradeName, String companyName, String cnpj, String phone,
                  String street, String number, String city, String state, String zipCode) {
        super();
        this.tradeName = tradeName;
        this.companyName = companyName;
        this.cnpj = cnpj;
        this.phone = phone;
        this.street = street;
        this.number = number;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
    }

    public Tenant(UUID id, boolean active, LocalDateTime dtInsert, LocalDateTime dtUpdate, LocalDateTime dtDelete,
                  String tradeName, String companyName, String cnpj, String phone,
                  String street, String number, String city, String state, String zipCode) {
        super(id, active, dtInsert, dtUpdate, dtDelete);
        this.tradeName = tradeName;
        this.companyName = companyName;
        this.cnpj = cnpj;
        this.phone = phone;
        this.street = street;
        this.number = number;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getPhone() {
        return phone;
    }

    public String getStreet() {
        return street;
    }

    public String getNumber() {
        return number;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getZipCode() {
        return zipCode;
    }
}
