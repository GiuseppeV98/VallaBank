package com.vallabank.backend.account;

public class CustomerNotFoundException extends IllegalArgumentException {

    public CustomerNotFoundException() {
        super("Customer not found");
    }
}