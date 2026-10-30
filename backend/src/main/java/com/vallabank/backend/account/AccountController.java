package com.vallabank.backend.account;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @RequestBody CreateAccountRequest request) {

        Account account = accountService.createAccount(
                request.customerId(),
                request.iban()
        );

        AccountResponse response = new AccountResponse(
                account.getId(),
                request.customerId(),
                account.getIban(),
                account.getAccountType(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
}
    @GetMapping("/{id}")
        public ResponseEntity<AccountResponse> getAccountById(
        @PathVariable UUID id) {

        return accountService.getAccountById(id)
        .map(account -> {

                AccountResponse response = new AccountResponse(
                        account.getId(),
                        account.getCustomer().getId(),
                        account.getIban(),
                        account.getAccountType(),
                        account.getCurrency(),
                        account.getBalance(),
                        account.getStatus()
                );

                return ResponseEntity.ok(response);
        })
        .orElseGet(() -> ResponseEntity.notFound().build());
}
}