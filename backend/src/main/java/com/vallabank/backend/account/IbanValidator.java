
package com.vallabank.backend.account;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IbanValidator implements ConstraintValidator<ValidIban, String> {

    @Override
    public boolean isValid(String iban, ConstraintValidatorContext context) {

        // I valori vuoti sono gestiti da @NotBlank
        if (iban == null || iban.isBlank()) {
            return true;
        }

        // Controllo struttura e lunghezza
        if (!iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")) {
            return false;
        }

        // Sposta i primi 4 caratteri alla fine
        String rearranged = iban.substring(4) + iban.substring(0, 4);

        int remainder = 0;

        for (char c : rearranged.toCharArray()) {

            if (Character.isDigit(c)) {
                remainder = (remainder * 10 + (c - '0')) % 97;
            } else {
                int number = c - 'A' + 10;
                remainder = (remainder * 100 + number) % 97;
            }
        }

        // IBAN valido se il resto è 1
        return remainder == 1;
    }
}
