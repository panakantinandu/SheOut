/**
 * Shared kernel - the only code every module is allowed to depend on
 * directly. Kept deliberately small: base entity, Result/Either type,
 * in-process domain event publisher, and the standard API error format.
 * Do not add module-specific logic here - if it's specific to one domain,
 * it belongs in that module instead.
 */
package com.sheout.sharedkernel;
