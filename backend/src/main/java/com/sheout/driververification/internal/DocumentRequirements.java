package com.sheout.driververification.internal;

import com.sheout.driververification.PartnerDocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which documents a partner needs before she can take a trip - the one place
 * that is decided.
 * <p>
 * Two steps, both configuration. Her vehicle decides which services she can
 * be sent on (a bike does bike rides and parcels), and each service has its
 * list of documents. What she needs is everything any of her services needs.
 * Asking it this way round means "parcels by bike need X" and "autos need a
 * fitness certificate" are each said once, and a new service is a new line
 * of config rather than an {@code if} somewhere.
 * <p>
 * Read as flat environment variables, one per vehicle and one per service,
 * for the reason FareConfiguration gives: the person changing this under
 * pressure should be able to find the name. Unknown document names fail
 * startup rather than being silently dropped from a safety rule.
 * <p>
 * The services-per-vehicle map repeats dispatch's matching rule (PARCEL is
 * offered to bikes). It is repeated, not imported, because this module
 * cannot depend on dispatch or users without a cycle; if dispatch's rule
 * changes, change this too.
 * <p>
 * The police certificate and background report are not listed here: they are
 * the evidence behind her police check, which is its own gate.
 */
@Component
public class DocumentRequirements {

    private static final Logger log = LoggerFactory.getLogger(DocumentRequirements.class);

    private final Map<String, Set<String>> servicesByVehicle;
    private final Map<String, Set<PartnerDocumentType>> requiredByService;
    private final Set<String> commercialInsuranceServices;
    private final List<Integer> reminderDays;
    private final boolean enforced;

    public DocumentRequirements(
            @Value("${PARTNER_SERVICES_BIKE:BIKE,PARCEL}") String bikeServices,
            @Value("${PARTNER_SERVICES_AUTO:AUTO}") String autoServices,
            @Value("${PARTNER_SERVICES_CAB:CAB}") String cabServices,
            @Value("${PARTNER_DOCS_BIKE:DRIVING_LICENCE,VEHICLE_RC,VEHICLE_INSURANCE,PUC}") String bikeDocs,
            @Value("${PARTNER_DOCS_AUTO:DRIVING_LICENCE,VEHICLE_RC,VEHICLE_INSURANCE,PUC,FITNESS_CERTIFICATE}") String autoDocs,
            @Value("${PARTNER_DOCS_CAB:DRIVING_LICENCE,VEHICLE_RC,VEHICLE_INSURANCE,PUC,FITNESS_CERTIFICATE}") String cabDocs,
            @Value("${PARTNER_DOCS_PARCEL:DRIVING_LICENCE,VEHICLE_RC,VEHICLE_INSURANCE,PUC}") String parcelDocs,
            // Services that carry a paying passenger. A private-use policy
            // can be refused for exactly those trips.
            @Value("${COMMERCIAL_INSURANCE_SERVICES:BIKE,AUTO,CAB}") String commercialServices,
            @Value("${DOCUMENT_REMINDER_DAYS:30,7,1}") String reminderDays,
            // The rollout switch, not a bypass: see the README. While false,
            // missing documents are warnings she is shown, not a refusal.
            @Value("${PARTNER_DOCUMENTS_REQUIRED_TO_GO_ONLINE:true}") boolean enforced) {
        Map<String, Set<String>> vehicles = new LinkedHashMap<>();
        vehicles.put("BIKE", names(bikeServices));
        vehicles.put("AUTO", names(autoServices));
        vehicles.put("CAB", names(cabServices));
        this.servicesByVehicle = Map.copyOf(vehicles);

        Map<String, Set<PartnerDocumentType>> services = new LinkedHashMap<>();
        services.put("BIKE", types("PARTNER_DOCS_BIKE", bikeDocs));
        services.put("AUTO", types("PARTNER_DOCS_AUTO", autoDocs));
        services.put("CAB", types("PARTNER_DOCS_CAB", cabDocs));
        services.put("PARCEL", types("PARTNER_DOCS_PARCEL", parcelDocs));
        this.requiredByService = Map.copyOf(services);

        for (Map.Entry<String, Set<String>> vehicle : servicesByVehicle.entrySet()) {
            for (String service : vehicle.getValue()) {
                if (!requiredByService.containsKey(service)) {
                    throw new IllegalArgumentException("PARTNER_SERVICES_" + vehicle.getKey()
                            + " names a service with no document list: " + service);
                }
            }
        }

        this.commercialInsuranceServices = names(commercialServices);
        this.reminderDays = Arrays.stream(reminderDays.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Integer::valueOf)
                .filter(d -> d > 0).distinct().sorted((a, b) -> b - a).toList();
        this.enforced = enforced;
        log.info("Partner documents {}required to go online; per service: {}", enforced ? "" : "NOT ", requiredByService);
    }

    /**
     * Everything a partner on this vehicle needs, in a stable order. An
     * unknown or missing vehicle gets every service's list: strict, because
     * the alternative is letting somebody whose vehicle we do not know work
     * on the shortest list.
     */
    public List<PartnerDocumentType> requiredFor(String vehicleType) {
        Set<PartnerDocumentType> required = EnumSet.noneOf(PartnerDocumentType.class);
        for (String service : servicesFor(vehicleType)) {
            required.addAll(requiredByService.getOrDefault(service, Set.of()));
        }
        return new ArrayList<>(required);
    }

    /** Whether her insurance must be for commercial use - true when any of her services carries passengers. */
    public boolean commercialInsuranceRequired(String vehicleType) {
        return servicesFor(vehicleType).stream().anyMatch(commercialInsuranceServices::contains);
    }

    /**
     * For an operator approving a policy without her vehicle to hand: the
     * strict answer. Every vehicle SheOut takes carries passengers today, so
     * this is the same answer in practice.
     */
    public boolean commercialInsuranceRequiredForAnyVehicle() {
        return servicesByVehicle.values().stream().flatMap(Collection::stream)
                .anyMatch(commercialInsuranceServices::contains);
    }

    /** Largest first: 30, 7, 1. */
    public List<Integer> reminderDays() {
        return reminderDays;
    }

    public boolean enforced() {
        return enforced;
    }

    private Set<String> servicesFor(String vehicleType) {
        Set<String> services = vehicleType == null ? null : servicesByVehicle.get(vehicleType.toUpperCase(Locale.ROOT));
        if (services != null) {
            return services;
        }
        Set<String> all = new LinkedHashSet<>();
        servicesByVehicle.values().forEach(all::addAll);
        return all;
    }

    private static Set<String> names(String csv) {
        Set<String> out = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String name = part.trim().toUpperCase(Locale.ROOT);
            if (!name.isEmpty()) {
                out.add(name);
            }
        }
        return Set.copyOf(out);
    }

    private static Set<PartnerDocumentType> types(String variable, String csv) {
        Set<PartnerDocumentType> out = EnumSet.noneOf(PartnerDocumentType.class);
        for (String name : names(csv)) {
            PartnerDocumentType type;
            try {
                type = PartnerDocumentType.valueOf(name);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(variable + " names an unknown document type: " + name);
            }
            if (type.policeEvidence()) {
                throw new IllegalArgumentException(variable + " cannot list " + name
                        + ": police evidence is checked by the police check, not as a vehicle document");
            }
            out.add(type);
        }
        return out;
    }
}
