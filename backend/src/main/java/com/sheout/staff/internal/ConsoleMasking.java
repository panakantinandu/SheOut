package com.sheout.staff.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.sheout.sharedkernel.privacy.Masking;
import com.sheout.sharedkernel.privacy.Pii;
import com.sheout.staff.Permission;
import com.sheout.staff.StaffContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Masks personal data in every response the console gets, at the one point
 * all of them pass through: turning objects into JSON.
 * <p>
 * WHY HERE AND NOT IN EACH ENDPOINT. Thirty-odd console views carry phone
 * numbers and addresses, built in four modules. Masking each by hand would be
 * thirty chances to forget one, and every new view another. Here it is the
 * default: a field named like a phone holding a mobile number is masked, and
 * so is anything marked {@link Pii}. Seeing the real value takes a reveal -
 * the permission, a fresh authenticator code and a typed reason, recorded.
 * <p>
 * ONLY FOR THE CONSOLE. The decision is made as each field is written: with a
 * staff member on the request (StaffContext) the value is masked; without one
 * - a rider's own app asking for her own trip - nothing changes.
 * <p>
 * Everyone in the console sees the masked form, owners included: an owner's
 * session is the one most worth stealing. The one exception is bank details
 * for the roles that send payouts (payouts.prepare), whose job is typing them
 * into the bank.
 */
@Component
class ConsoleMasking extends SimpleModule {

    /** Plain-text fields that hold a trip's start or end address. */
    private static final Set<String> ADDRESS_FIELDS = Set.of("pickup", "drop", "pickuplabel", "droplabel");

    ConsoleMasking() {
        super("sheout-console-masking");
        setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription description,
                                                             List<BeanPropertyWriter> writers) {
                for (int i = 0; i < writers.size(); i++) {
                    BeanPropertyWriter writer = writers.get(i);
                    Pii marked = writer.getAnnotation(Pii.class);
                    boolean text = writer.getType().getRawClass() == String.class;
                    String name = writer.getName().toLowerCase(Locale.ROOT);
                    if (marked != null) {
                        writers.set(i, new MaskingWriter(writer, marked.value(), false));
                    } else if (text && name.contains("phone")) {
                        writers.set(i, new MaskingWriter(writer, null, true));
                    } else if (text && ADDRESS_FIELDS.contains(name)) {
                        writers.set(i, new MaskingWriter(writer, Pii.Kind.ADDRESS, false));
                    }
                }
                return writers;
            }
        });
    }

    static final class MaskingWriter extends BeanPropertyWriter {

        private final Pii.Kind kind;
        private final boolean phone;

        MaskingWriter(BeanPropertyWriter base, Pii.Kind kind, boolean phone) {
            super(base);
            this.kind = kind;
            this.phone = phone;
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider provider) throws Exception {
            if (StaffContext.current().isEmpty()) {
                super.serializeAsField(bean, gen, provider);
                return;
            }
            Object value = get(bean);
            if (value == null) {
                super.serializeAsField(bean, gen, provider);
                return;
            }
            if (phone) {
                if (value instanceof String s && Masking.looksLikePersonalPhone(s)) {
                    gen.writeStringField(getName(), Masking.phone(s));
                } else {
                    // A staff member's name in an "...byPhone" field, an insurer's helpline.
                    super.serializeAsField(bean, gen, provider);
                }
                return;
            }
            switch (kind) {
                case ADDRESS -> gen.writeStringField(getName(), Masking.area(value.toString()));
                case PAN -> gen.writeStringField(getName(), Masking.pan(value.toString()));
                case COORDINATE -> {
                    if (value instanceof Number n) {
                        gen.writeNumberField(getName(), Masking.coordinate(n.doubleValue()));
                    } else {
                        super.serializeAsField(bean, gen, provider);
                    }
                }
                case BANK -> {
                    if (StaffContext.has(Permission.PAYOUTS_PREPARE)) {
                        super.serializeAsField(bean, gen, provider);
                    } else {
                        gen.writeStringField(getName(), Masking.bank(value.toString()));
                    }
                }
            }
        }
    }
}
