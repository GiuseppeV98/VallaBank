
package com.vallabank.backend.transaction;

import java.util.List;
import java.util.UUID;

public record TransactionHistoryResponse(
        UUID accountId,
        List<TransactionItemResponse> transactions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
