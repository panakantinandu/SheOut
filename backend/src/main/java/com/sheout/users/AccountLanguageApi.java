package com.sheout.users;

import java.util.Optional;
import java.util.UUID;

/**
 * The language an account chose in the app - for anything the server writes
 * to that person, such as a notification, so it reaches her in the language
 * she reads the app in.
 */
public interface AccountLanguageApi {

    /** Empty when she has not chosen one. */
    Optional<AppLanguage> languageOf(UUID accountId);
}
