package com.sheout.sharedkernel.geo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The service boundary, as the apps should draw and warn about it.
 * <p>
 * The apps used to carry their own copy of the radius, "kept in step" by
 * hand. It drifted: the rider app said 150km while production refused
 * anything past 25km, so a rider in a town the app called covered filled in
 * a whole trip only to be refused at the end. Now there is one number, here,
 * and the apps ask for it.
 * <p>
 * Public, because the boundary is not a secret and an app may want it before
 * anyone signs in. Asked once per launch; an app holding an old value is
 * caught by the same server-side check that has always been the real gate.
 */
@RestController
public class ServiceAreaController {

    private final ServiceArea serviceArea;

    public ServiceAreaController(ServiceArea serviceArea) {
        this.serviceArea = serviceArea;
    }

    public record ServiceAreaResponse(double centreLat, double centreLng, double radiusKm, String centreName) {
    }

    @GetMapping("/api/v1/service-area")
    public ResponseEntity<ServiceAreaResponse> serviceArea() {
        return ResponseEntity.ok(new ServiceAreaResponse(serviceArea.centreLat(), serviceArea.centreLng(),
                serviceArea.radiusKm(), serviceArea.centreName()));
    }
}
