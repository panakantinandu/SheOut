package com.sheout.dispatch.internal;

import com.sheout.booking.BookingCategory;
import com.sheout.sharedkernel.warmup.WarmUp;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * The partner search a rider's first screen runs, and a partner's first
 * polls - reads only. It never records a location: a made-up partner in the
 * geo index would show up on riders' maps. See sharedkernel.warmup.WarmUp.
 */
@Component
class DispatchWarmUp implements WarmUp {

    private final DispatchService dispatch;

    DispatchWarmUp(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @Override
    public String name() {
        return "dispatch";
    }

    @Override
    public void inProcess() {
        dispatch.previewNearby(17.4486, 78.3908, BookingCategory.BIKE);
        dispatch.findActiveOffer(UUID.randomUUID());
    }

    @Override
    public List<Request> requests() {
        return List.of(
                Request.get("/api/v1/dispatch/nearby-drivers?lat=17.4486&lng=78.3908&category=BIKE"),
                Request.post("/api/v1/dispatch/location", "{\"lat\":17.4486,\"lng\":78.3908}"),
                Request.get("/api/v1/dispatch/offers/me"),
                Request.get("/api/v1/dispatch/bookings/" + UUID.randomUUID() + "/driver-location"));
    }
}
