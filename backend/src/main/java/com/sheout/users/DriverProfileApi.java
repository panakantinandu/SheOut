package com.sheout.users;

import java.util.Optional;
import java.util.UUID;

public interface DriverProfileApi {

    Optional<DriverProfileSummary> findByAccountId(UUID accountId);
}
