
package com.vallabank.backend.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountTransactionRepository
        extends JpaRepository<AccountTransaction, UUID> {

    Page<AccountTransaction> findByAccount_Id(
            UUID accountId,
            Pageable pageable
    );
    List<AccountTransaction> findByTransferId(UUID transferId);
}
