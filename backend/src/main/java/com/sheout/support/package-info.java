/**
 * Support module - how a rider or a partner reaches a person at SheOut.
 * <p>
 * Small, and deliberately its own module rather than a constant in two
 * frontends or a field on chat. Both apps had their own hardcoded support
 * number, and they were different: support answered on one number from the
 * rider's app and another from the partner's, and correcting either meant
 * shipping a frontend. One value, served from one place, changed by
 * configuration.
 * <p>
 * This exists because direct rider-to-partner calling does not. Handing a
 * stranger a woman's real phone number is the risk this product was built
 * to remove: it cannot be withdrawn once given and it outlives the trip it
 * was given for. Everything routine between the two of them goes through
 * booking-scoped chat; anything that genuinely needs a voice comes here, to
 * somebody who can hear both sides and act.
 * <p>
 * Nothing to do with SOS. The emergency path - a rider's own emergency
 * contacts, and the 112 dial - is separate, stays separate, and must never
 * be routed through a support queue.
 * <p>
 * TICKETS. A rider or a partner raises an issue and the two sides write back
 * and forth until an operator resolves it. Tickets depend on booking only
 * through BookingApi (to check a linked booking is the raiser's) and on
 * auth only through AuthApi (to check an assignee is an operator). Creation
 * publishes SupportTicketRaised; an operator's visible reply publishes
 * SupportReplyPosted, which the notifications module turns into an SMS -
 * this module never sends anything itself.
 * <p>
 * A SAFETY_CONCERN ticket is HIGH priority and sorts to the top of the
 * operator queue, and the console links it to any SOS alert on the same
 * booking - but it is still a ticket. It is read when someone reaches it,
 * which is exactly why both apps point a person in danger at SOS instead.
 */
package com.sheout.support;
