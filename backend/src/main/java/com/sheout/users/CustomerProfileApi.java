package com.sheout.users;

import java.util.Optional;
import java.util.UUID;

public interface CustomerProfileApi {

    Optional<CustomerProfileSummary> findByAccountId(UUID accountId);
}
