package com.verdant.salon_ecomm.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Address {

    @Column(name = "address_line1", length = 255)
    private String line1;

    @Column(name = "address_line2", length = 255)
    private String line2;

    @Column(name = "address_city", length = 100)
    private String city;

    @Column(name = "address_state", length = 100)
    private String state;

    @Column(name = "address_postal", length = 20)
    private String postal;

    @Column(name = "address_country", length = 2)
    private String country = "PH";
}
