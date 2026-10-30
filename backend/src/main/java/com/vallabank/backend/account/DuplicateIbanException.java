package com.vallabank.backend.account;

public class DuplicateIbanException extends IllegalArgumentException {

    public DuplicateIbanException() {
        super("IBAN already exists");
    }
}