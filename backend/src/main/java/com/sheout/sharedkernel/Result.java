package com.sheout.sharedkernel;

/**
 * A simple Result/Either type for error handling without exceptions-as-control-flow.
 * Use this at module boundaries (public interfaces) so callers get typed
 * success/failure instead of having to catch exceptions.
 *
 * <pre>
 *     Result&lt;Booking, BookingError&gt; result = bookingModuleApi.createBooking(request);
 *     if (result.isFailure()) {
 *         return handle(result.error());
 *     }
 *     Booking booking = result.value();
 * </pre>
 * <p>
 * Prefer the {@link #isSuccess()}/{@link #isFailure()} + {@link #value()}/
 * {@link #error()} style shown above over a {@code switch} with parameterized
 * type patterns (e.g. {@code case Result.Success<Booking, BookingError> s}) -
 * Java doesn't allow pattern matching against a non-wildcard parameterized
 * generic type, so that switch won't compile. A wildcard pattern
 * ({@code case Result.Success<?, ?> s}) does compile if a switch is ever
 * genuinely clearer than the if/else form, but loses the concrete type.
 */
public sealed interface Result<T, E> permits Result.Success, Result.Failure {

    static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }

    boolean isSuccess();

    default boolean isFailure() {
        return !isSuccess();
    }

    /**
     * The success value. Throws if this is a Failure - callers should check
     * {@link #isSuccess()} (or pattern-match on the sealed type) first.
     */
    T value();

    /**
     * The failure error. Throws if this is a Success - callers should check
     * {@link #isFailure()} (or pattern-match on the sealed type) first.
     */
    E error();

    record Success<T, E>(T value) implements Result<T, E> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public E error() {
            throw new IllegalStateException("Result is a Success, has no error");
        }
    }

    record Failure<T, E>(E error) implements Result<T, E> {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public T value() {
            throw new IllegalStateException("Result is a Failure, has no value");
        }
    }
}
