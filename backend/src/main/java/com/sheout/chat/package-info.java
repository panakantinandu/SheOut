/**
 * Booking-scoped text chat between a rider and their partner.
 * <p>
 * Its own module rather than a corner of booking, for the reasons this
 * codebase already separates modules on: it owns its own table and its own
 * lifecycle rules, and those rules have nothing to do with a booking's state
 * machine. Booking decides when a trip starts and ends; chat decides who may
 * read a thread and until when they may add to it.
 * <p>
 * It depends on booking only through {@link com.sheout.booking.BookingApi}
 * (participants and status), exactly as payments and dispatch do.
 * <p>
 * WHY THIS EXISTS AT ALL: it replaces the expectation of a rider and a
 * partner phoning each other. On a women-only platform, handing a stranger
 * someone's personal mobile number to arrange a pickup is a permanent
 * disclosure in exchange for a five-minute need. A thread that lives and
 * dies with the trip costs nothing after it, and still leaves a record if
 * the trip goes wrong.
 */
package com.sheout.chat;
