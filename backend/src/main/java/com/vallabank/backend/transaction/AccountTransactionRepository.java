
package com.vallabank.backend.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AccountTransactionRepository
        extends JpaRepository<AccountTransaction, UUID> {
}
