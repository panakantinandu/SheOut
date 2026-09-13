/**
 * File storage for documents and images, shared because more than one
 * module needs it.
 * <p>
 * It began inside driver-verification, where Aadhaar uploads were the only
 * files this system held. Two modules store files now - verification keeps
 * identity and vehicle documents, users keeps a partner's profile photo -
 * and the modular rule here is that a module may not reach into another
 * module's internals. The alternatives were making driver-verification a
 * storage provider for everybody else, which muddies what that module is
 * for, or building a second storage mechanism, which is exactly the
 * duplication to avoid. So it moved here.
 * <p>
 * This is infrastructure, not domain logic, which is what makes it a fit
 * for the shared kernel alongside the event publisher: nothing in here
 * knows what a verification or a profile is. It takes bytes and gives back
 * an opaque key.
 */
package com.sheout.sharedkernel.storage;
