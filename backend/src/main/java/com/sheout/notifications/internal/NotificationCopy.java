package com.sheout.notifications.internal;

import com.sheout.booking.BookingCategory;
import com.sheout.users.AccountLanguageApi;
import com.sheout.users.AppLanguage;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * What riders and partners are told, in the language each of them chose.
 * <p>
 * The apps have been in English, Hindi and Telugu all along, but every
 * notification was written in English on the server, so somebody using the
 * app in Telugu was alerted - "Your partner is on the way", "Payment due" -
 * in a language she may not read. Each message is now looked up here by the
 * recipient's own language, with English where she has not chosen one.
 * <p>
 * The English text is kept alongside each message, because SMS is sent from
 * it: an SMS in India must match a DLT-registered template word for word,
 * and until a Hindi and a Telugu template are registered for each message
 * the registered one is the English. Operator alerts are not here - the
 * operations team works in English.
 * <p>
 * The Hindi and Telugu are for a native speaker to review before launch, the
 * same as the apps' safety copy.
 */
@Component
public class NotificationCopy {

    /** One message in the recipient's language, plus the English an SMS must use. */
    public record Localized(String title, String body, String englishTitle, String englishBody) {
    }

    private final AccountLanguageApi languages;

    NotificationCopy(AccountLanguageApi languages) {
        this.languages = languages;
    }

    public AppLanguage languageOf(UUID accountId) {
        return languages.languageOf(accountId).orElse(AppLanguage.EN);
    }

    /**
     * params is asked once per language, because some of what is filled in
     * is itself worded - "bike taxi" in the English an SMS uses, "बाइक टैक्सी"
     * in the Hindi she reads in the app.
     */
    public Localized render(UUID recipient, String key, java.util.function.Function<AppLanguage, Map<String, String>> params) {
        AppLanguage language = languageOf(recipient);
        String[] english = template(AppLanguage.EN, key);
        String[] local = template(language, key);
        Map<String, String> localParams = params.apply(language);
        Map<String, String> englishParams = params.apply(AppLanguage.EN);
        return new Localized(fill(local[0], localParams), fill(local[1], localParams),
                fill(english[0], englishParams), fill(english[1], englishParams));
    }

    /** "bike taxi", "बाइक टैक्सी", "బైక్ టాక్సీ" - for filling into a sentence. */
    String categoryName(BookingCategory category, AppLanguage language) {
        String key = "category." + category.name();
        return one(language, key);
    }

    public String one(AppLanguage language, String key) {
        String value = TEXT.get(language).get(key);
        return value != null ? value : TEXT.get(AppLanguage.EN).getOrDefault(key, key);
    }

    /**
     * Safety text: her language, then the English beneath it, until a native
     * speaker has checked the translation - the rule the apps follow for
     * their SOS screen. A message that says "call 112" must be understood
     * even if the translation is wrong.
     */
    public String safety(AppLanguage language, String key, Map<String, String> params) {
        String english = fill(one(AppLanguage.EN, key), params);
        if (language == AppLanguage.EN) {
            return english;
        }
        return fill(one(language, key), params) + "\n" + english;
    }

    private static String[] template(AppLanguage language, String key) {
        String title = TEXT.get(language).get(key + ".title");
        String body = TEXT.get(language).get(key + ".body");
        if (title == null || body == null) {
            title = TEXT.get(AppLanguage.EN).get(key + ".title");
            body = TEXT.get(AppLanguage.EN).get(key + ".body");
        }
        if (title == null || body == null) {
            throw new IllegalArgumentException("No notification copy for " + key);
        }
        return new String[] {title, body};
    }

    private static String fill(String template, Map<String, String> params) {
        String out = template;
        for (Map.Entry<String, String> p : params.entrySet()) {
            out = out.replace("{" + p.getKey() + "}", p.getValue() == null ? "" : p.getValue());
        }
        return out;
    }

    private static final Map<AppLanguage, Map<String, String>> TEXT = Map.of(
            AppLanguage.EN, Map.ofEntries(
                    Map.entry("destinationChangeRequested.title", "Your rider wants to change the destination"),
                    Map.entry("destinationChangeRequested.body", "New fare {fare} (was {was}). Open the trip to accept or decline within {minutes} min."),
                    Map.entry("destinationChangedRider.title", "Destination changed"),
                    Map.entry("destinationChangedRider.body", "Your partner accepted. Your new fare is {fare}."),
                    Map.entry("destinationChangedPartner.title", "Destination changed"),
                    Map.entry("destinationChangedPartner.body", "The trip now goes to the new drop. Fare {fare}."),
                    Map.entry("destinationChangeDeclined.title", "Your partner couldn't take the new destination"),
                    Map.entry("destinationChangeDeclined.body", "Your trip continues to your original drop. Fare unchanged: {fare}."),
                    Map.entry("bookingRequested.title", "Booking requested"),
                    Map.entry("bookingRequested.body", "We're finding a {category} partner near you."),
                    Map.entry("driverOffer.title", "New {category} trip request"),
                    Map.entry("driverOffer.body", "{km} km from you. Offers last {seconds} seconds, so open SheOut to accept."),
                    Map.entry("bookingAccepted.title", "{driver} is on the way"),
                    Map.entry("bookingAccepted.body", "Check her photo and vehicle number in the app before you get in."),
                    Map.entry("driverArriving.title", "{driver} is arriving"),
                    Map.entry("driverArriving.body", "She is almost at your pickup. Have your pickup code ready."),
                    Map.entry("bookingCompleted.title", "You have arrived: payment due"),
                    Map.entry("bookingCompleted.body", "Fare {fare}. Pay in the app, from your SheOut wallet or online, to finish the trip."),
                    Map.entry("cancelledByPartner.title", "Your booking was cancelled"),
                    Map.entry("cancelledByPartner.body", "Your partner had to cancel. You have not been charged, so book again whenever you are ready."),
                    Map.entry("cancelledForRider.title", "Your booking was cancelled"),
                    Map.entry("cancelledForRider.body", "You have not been charged. Book again whenever you are ready."),
                    Map.entry("cancelledForPartner.title", "Trip cancelled"),
                    Map.entry("cancelledForPartner.body", "The rider cancelled this trip. You do not need to go to the pickup."),
                    Map.entry("noDrivers.title", "No partners available right now"),
                    Map.entry("noDrivers.body", "Nobody nearby could take this trip. Nothing was charged. Try again in a few minutes."),
                    Map.entry("verificationRejected.title", "We could not verify your ID"),
                    Map.entry("verificationRejected.body", "{reason} You can send another photo from Identity Verification in the app."),
                    Map.entry("profileChangeApproved.title", "Your profile change is approved"),
                    Map.entry("profileChangeApproved.body", "Riders now see your updated details."),
                    Map.entry("profileChangeRejected.title", "Your profile change was not approved"),
                    Map.entry("profileChangeRejected.body", "Open your profile to see why. Your earlier details are still in use."),
                    Map.entry("tripCheckIn.title", "Is everything OK on your trip?"),
                    Map.entry("tripCheckIn.body", "Your trip has stopped or is taking much longer than expected. If you need help, open SheOut and press SOS, or call 112."),
                    Map.entry("partnerLeft.title", "Your partner had to cancel"),
                    Map.entry("partnerLeft.body", "We are finding you another partner now. You do not need to book again."),
                    Map.entry("verifiedPartner.title", "Your account is verified"),
                    Map.entry("verifiedPartner.body", "You can go online and accept trips."),
                    Map.entry("verifiedRider.title", "Your account is verified"),
                    Map.entry("verifiedRider.body", "You can now book rides and deliveries."),
                    Map.entry("supportReply.title", "Support replied to your ticket"),
                    Map.entry("supportReply.body", "Open Help & Support in the app to read the reply."),
                    Map.entry("payoutPaid.title", "Payout sent: {amount}"),
                    Map.entry("payoutPaid.body", "SheOut has sent your payout. Bank or UPI reference: {reference}."),
                    Map.entry("incentive.title", "Bonus earned: {amount}"),
                    Map.entry("incentive.body", "{name}: added to your SheOut wallet on top of your trip earnings."),
                    Map.entry("referralJoined.title", "A friend joined with your code"),
                    Map.entry("referralJoined.body", "You both get {amount} when she takes her first paid ride."),
                    Map.entry("referralJoinedPartner.title", "A partner joined with your code"),
                    Map.entry("referralJoinedPartner.body", "You get {amount} in your wallet when she completes her first paid trip."),
                    Map.entry("referralJoinedPlain.body", "Thanks for inviting her to SheOut."),
                    Map.entry("sellerApproved.title", "Your SheOut Seller shop is approved"),
                    Map.entry("sellerApproved.body", "Pay the one-time {amount} listing fee to go live."),
                    Map.entry("sellerRejected.title", "Your shop needs a few changes"),
                    Map.entry("sellerRejected.body", "Open SheOut Seller to see what to change, then send it again."),
                    Map.entry("sellerLive.title", "Your shop is live"),
                    Map.entry("sellerLive.body", "Customers can now find you in SheOut Seller."),
                    Map.entry("sellerSuspended.title", "Your shop has been paused"),
                    Map.entry("sellerSuspended.body", "It is hidden from customers for now. Open SheOut Seller to see why."),
                    Map.entry("referralRewardedReferrer.title", "You earned {amount} ride credit"),
                    Map.entry("referralRewardedReferrer.body", "Your friend took her first ride. The credit is used on your next rides."),
                    Map.entry("referralRewardedReferee.title", "Your {amount} welcome credit is here"),
                    Map.entry("referralRewardedReferee.body", "Thanks for joining with a friend's code. The credit is used on your next rides."),
                    Map.entry("receipt.title", "Receipt: {amount} paid"),
                    Map.entry("receipt.body", "Paid {how} on {when} for your {category} trip from {from} to {to}."),
                    Map.entry("receipt.cash", "in cash to your partner"),
                    Map.entry("receipt.sheoutWallet", "from your SheOut wallet"),
                    Map.entry("receipt.promoCredit", "by your SheOut promotional credit"),
                    Map.entry("receipt.online", "online by {method}"),
                    Map.entry("method.UPI", "UPI"),
                    Map.entry("method.CARD", "card"),
                    Map.entry("method.NETBANKING", "netbanking"),
                    Map.entry("method.WALLET", "wallet"),
                    Map.entry("method.OTHER", "online payment"),
                    Map.entry("yourPartner", "Your partner"),
                    Map.entry("sosSent.title", "SOS alert sent"),
                    Map.entry("sosNoContacts", "You have no emergency contacts saved, so nobody was texted. Add one from your profile."),
                    Map.entry("sosNoneReached", "We could not text any of your emergency contacts. Call 112 if you are in danger."),
                    Map.entry("sosSomeReached", "Your location was texted to {notified} of {total} emergency contacts."),
                    Map.entry("sosOneReached", "Your location was texted to your emergency contact."),
                    Map.entry("reengage.rider1.title", "We miss you on SheOut"),
                    Map.entry("reengage.rider1.body", "Going somewhere today? Book a ride with a verified woman partner in a few taps."),
                    Map.entry("reengage.rider2.title", "Your safe ride is a tap away"),
                    Map.entry("reengage.rider2.body", "\"She who travels safely travels far.\" Book your next SheOut ride today."),
                    Map.entry("reengage.rider3.title", "It's been a while!"),
                    Map.entry("reengage.rider3.body", "Rides, parcels and shops run by women near you, all in SheOut. Open the app to see what is new."),
                    Map.entry("reengage.partner1.title", "Ready to earn today?"),
                    Map.entry("reengage.partner1.body", "Riders near you are booking. Go online in SheOut Partner whenever you are ready."),
                    Map.entry("reengage.partner2.title", "Your riders miss you"),
                    Map.entry("reengage.partner2.body", "\"Every ride you give moves another woman forward.\" Go online today and start earning."),
                    Map.entry("reengage.partner3.title", "Come back to SheOut Partner"),
                    Map.entry("reengage.partner3.body", "It has been a month. Open the app, go online, and pick up where you left off."),
                    Map.entry("reengage.partnerVerify.title", "Finish your verification"),
                    Map.entry("reengage.partnerVerify.body", "A few steps and you can start earning with SheOut. Open the app to continue."),
                    Map.entry("category.BIKE", "bike taxi"),
                    Map.entry("category.AUTO", "auto"),
                    Map.entry("category.CAB", "cab"),
                    Map.entry("category.PARCEL", "parcel delivery"),
                    Map.entry("category.LUNCHBOX", "lunch box delivery")),
            AppLanguage.HI, Map.ofEntries(
                    Map.entry("destinationChangeRequested.title", "आपकी राइडर गंतव्य बदलना चाहती हैं"),
                    Map.entry("destinationChangeRequested.body", "नया किराया {fare} (पहले {was})। {minutes} मिनट में स्वीकार या अस्वीकार करने के लिए ट्रिप खोलें।"),
                    Map.entry("destinationChangedRider.title", "गंतव्य बदल गया"),
                    Map.entry("destinationChangedRider.body", "आपकी पार्टनर ने स्वीकार कर लिया। आपका नया किराया {fare} है।"),
                    Map.entry("destinationChangedPartner.title", "गंतव्य बदल गया"),
                    Map.entry("destinationChangedPartner.body", "ट्रिप अब नए ड्रॉप पर जाएगी। किराया {fare}।"),
                    Map.entry("destinationChangeDeclined.title", "आपकी पार्टनर नया गंतव्य नहीं ले सकीं"),
                    Map.entry("destinationChangeDeclined.body", "आपकी ट्रिप पहले वाले ड्रॉप तक जारी है। किराया वही है: {fare}।"),
                    Map.entry("bookingRequested.title", "बुकिंग का अनुरोध किया गया"),
                    Map.entry("bookingRequested.body", "हम आपके पास एक {category} पार्टनर ढूँढ रहे हैं।"),
                    Map.entry("driverOffer.title", "नया {category} ट्रिप अनुरोध"),
                    Map.entry("driverOffer.body", "आपसे {km} किमी दूर। ऑफ़र {seconds} सेकंड तक रहता है, स्वीकार करने के लिए SheOut खोलें।"),
                    Map.entry("bookingAccepted.title", "{driver} आ रही हैं"),
                    Map.entry("bookingAccepted.body", "बैठने से पहले ऐप में उनकी फ़ोटो और वाहन नंबर देख लें।"),
                    Map.entry("driverArriving.title", "{driver} पहुँचने वाली हैं"),
                    Map.entry("driverArriving.body", "वे आपके पिकअप के लगभग पास हैं। अपना पिकअप कोड तैयार रखें।"),
                    Map.entry("bookingCompleted.title", "आप पहुँच गई हैं: भुगतान बाकी है"),
                    Map.entry("bookingCompleted.body", "किराया {fare}। ट्रिप पूरी करने के लिए ऐप में, अपने SheOut वॉलेट से या ऑनलाइन भुगतान करें।"),
                    Map.entry("cancelledByPartner.title", "आपकी बुकिंग रद्द हो गई"),
                    Map.entry("cancelledByPartner.body", "आपकी पार्टनर को रद्द करना पड़ा। आपसे कोई शुल्क नहीं लिया गया, जब चाहें फिर से बुक करें।"),
                    Map.entry("cancelledForRider.title", "आपकी बुकिंग रद्द हो गई"),
                    Map.entry("cancelledForRider.body", "आपसे कोई शुल्क नहीं लिया गया, जब चाहें फिर से बुक करें।"),
                    Map.entry("cancelledForPartner.title", "ट्रिप रद्द हो गई"),
                    Map.entry("cancelledForPartner.body", "राइडर ने यह ट्रिप रद्द कर दी। आपको पिकअप पर जाने की ज़रूरत नहीं है।"),
                    Map.entry("noDrivers.title", "अभी कोई पार्टनर उपलब्ध नहीं है"),
                    Map.entry("noDrivers.body", "पास में कोई यह ट्रिप नहीं ले सका। कोई शुल्क नहीं लिया गया, कुछ मिनट बाद फिर कोशिश करें।"),
                    Map.entry("verificationRejected.title", "हम आपकी ID सत्यापित नहीं कर सके"),
                    Map.entry("verificationRejected.body", "{reason} आप ऐप में पहचान सत्यापन से दूसरी फ़ोटो भेज सकती हैं।"),
                    Map.entry("profileChangeApproved.title", "आपकी प्रोफ़ाइल में बदलाव मंज़ूर हुआ"),
                    Map.entry("profileChangeApproved.body", "अब राइडर आपकी नई जानकारी देखेंगी।"),
                    Map.entry("profileChangeRejected.title", "आपकी प्रोफ़ाइल में बदलाव मंज़ूर नहीं हुआ"),
                    Map.entry("profileChangeRejected.body", "वजह देखने के लिए अपनी प्रोफ़ाइल खोलें। आपकी पुरानी जानकारी ही इस्तेमाल हो रही है।"),
                    Map.entry("tripCheckIn.title", "क्या आपकी यात्रा में सब ठीक है?"),
                    Map.entry("tripCheckIn.body", "आपकी यात्रा रुकी हुई है या उम्मीद से काफ़ी लंबी चल रही है। मदद चाहिए तो SheOut खोलकर SOS दबाएँ, या 112 पर कॉल करें।"),
                    Map.entry("partnerLeft.title", "आपकी पार्टनर को रद्द करना पड़ा"),
                    Map.entry("partnerLeft.body", "हम अभी आपके लिए दूसरी पार्टनर ढूँढ रहे हैं। आपको फिर से बुक करने की ज़रूरत नहीं है।"),
                    Map.entry("verifiedPartner.title", "आपका अकाउंट सत्यापित हो गया है"),
                    Map.entry("verifiedPartner.body", "अब आप ऑनलाइन जाकर ट्रिप स्वीकार कर सकती हैं।"),
                    Map.entry("verifiedRider.title", "आपका अकाउंट सत्यापित हो गया है"),
                    Map.entry("verifiedRider.body", "अब आप राइड और डिलीवरी बुक कर सकती हैं।"),
                    Map.entry("supportReply.title", "सपोर्ट ने आपके टिकट का जवाब दिया"),
                    Map.entry("supportReply.body", "जवाब पढ़ने के लिए ऐप में सहायता और सपोर्ट खोलें।"),
                    Map.entry("payoutPaid.title", "पेआउट भेजा गया: {amount}"),
                    Map.entry("payoutPaid.body", "SheOut ने आपका पेआउट भेज दिया है। बैंक या UPI संदर्भ: {reference}।"),
                    Map.entry("incentive.title", "बोनस मिला: {amount}"),
                    Map.entry("incentive.body", "{name}: आपकी ट्रिप कमाई के ऊपर आपके SheOut वॉलेट में जोड़ा गया।"),
                    Map.entry("referralJoined.title", "एक दोस्त आपके कोड से जुड़ीं"),
                    Map.entry("referralJoined.body", "जब वह अपनी पहली पेड राइड लेंगी, आप दोनों को {amount} मिलेंगे।"),
                    Map.entry("referralJoinedPartner.title", "एक पार्टनर आपके कोड से जुड़ीं"),
                    Map.entry("referralJoinedPartner.body", "जब वह अपनी पहली पेड ट्रिप पूरी करेंगी, आपके वॉलेट में {amount} आएंगे।"),
                    Map.entry("referralJoinedPlain.body", "उन्हें SheOut पर बुलाने के लिए धन्यवाद।"),
                    Map.entry("sellerApproved.title", "आपकी SheOut Seller दुकान मंज़ूर हो गई"),
                    Map.entry("sellerApproved.body", "लाइव होने के लिए एक बार की {amount} लिस्टिंग फ़ीस दें।"),
                    Map.entry("sellerRejected.title", "आपकी दुकान में कुछ बदलाव चाहिए"),
                    Map.entry("sellerRejected.body", "SheOut Seller खोलकर देखें क्या बदलना है, फिर दोबारा भेजें।"),
                    Map.entry("sellerLive.title", "आपकी दुकान लाइव है"),
                    Map.entry("sellerLive.body", "अब ग्राहक आपको SheOut Seller में ढूंढ सकते हैं।"),
                    Map.entry("sellerSuspended.title", "आपकी दुकान रोक दी गई है"),
                    Map.entry("sellerSuspended.body", "अभी यह ग्राहकों को नहीं दिखेगी। कारण जानने के लिए SheOut Seller खोलें।"),
                    Map.entry("referralRewardedReferrer.title", "आपको {amount} राइड क्रेडिट मिला"),
                    Map.entry("referralRewardedReferrer.body", "आपकी दोस्त ने अपनी पहली राइड ली। यह क्रेडिट आपकी अगली राइड्स में लगेगा।"),
                    Map.entry("referralRewardedReferee.title", "आपका {amount} वेलकम क्रेडिट आ गया"),
                    Map.entry("referralRewardedReferee.body", "दोस्त के कोड से जुड़ने के लिए धन्यवाद। यह क्रेडिट आपकी अगली राइड्स में लगेगा।"),
                    Map.entry("receipt.title", "रसीद: {amount} का भुगतान हुआ"),
                    Map.entry("receipt.body", "{when} को {from} से {to} तक की आपकी {category} ट्रिप के लिए {how} भुगतान किया गया।"),
                    Map.entry("receipt.cash", "आपकी पार्टनर को नकद"),
                    Map.entry("receipt.sheoutWallet", "आपके SheOut वॉलेट से"),
                    Map.entry("receipt.promoCredit", "आपके SheOut प्रमोशनल क्रेडिट से"),
                    Map.entry("receipt.online", "{method} से ऑनलाइन"),
                    Map.entry("method.UPI", "UPI"),
                    Map.entry("method.CARD", "कार्ड"),
                    Map.entry("method.NETBANKING", "नेटबैंकिंग"),
                    Map.entry("method.WALLET", "वॉलेट"),
                    Map.entry("method.OTHER", "ऑनलाइन भुगतान"),
                    Map.entry("yourPartner", "आपकी पार्टनर"),
                    Map.entry("sosSent.title", "SOS अलर्ट भेजा गया"),
                    Map.entry("sosNoContacts", "आपके कोई इमरजेंसी संपर्क सेव नहीं हैं, इसलिए किसी को SMS नहीं गया। अपनी प्रोफ़ाइल से एक जोड़ें।"),
                    Map.entry("sosNoneReached", "हम आपके किसी भी इमरजेंसी संपर्क को SMS नहीं भेज सके। खतरे में हों तो 112 पर कॉल करें।"),
                    Map.entry("sosSomeReached", "आपकी लोकेशन {total} में से {notified} इमरजेंसी संपर्कों को SMS की गई।"),
                    Map.entry("sosOneReached", "आपकी लोकेशन आपके इमरजेंसी संपर्क को SMS की गई।"),
                    Map.entry("reengage.rider1.title", "SheOut पर हम आपको याद कर रहे हैं"),
                    Map.entry("reengage.rider1.body", "आज कहीं जाना है? कुछ ही टैप में सत्यापित महिला पार्टनर के साथ राइड बुक करें।"),
                    Map.entry("reengage.rider2.title", "आपकी सुरक्षित राइड बस एक टैप दूर"),
                    Map.entry("reengage.rider2.body", "\"जो सुरक्षित चलती है, वह दूर तक जाती है।\" आज ही अपनी अगली SheOut राइड बुक करें।"),
                    Map.entry("reengage.rider3.title", "काफ़ी समय हो गया!"),
                    Map.entry("reengage.rider3.body", "राइड, पार्सल और आपके पास की महिलाओं की दुकानें, सब SheOut में। नया क्या है, देखने के लिए ऐप खोलें।"),
                    Map.entry("reengage.partner1.title", "आज कमाने के लिए तैयार?"),
                    Map.entry("reengage.partner1.body", "आपके पास राइडर बुकिंग कर रही हैं। जब तैयार हों, SheOut Partner में ऑनलाइन हो जाएँ।"),
                    Map.entry("reengage.partner2.title", "आपकी राइडर आपको याद कर रही हैं"),
                    Map.entry("reengage.partner2.body", "\"आपकी हर राइड एक और महिला को आगे बढ़ाती है।\" आज ऑनलाइन हों और कमाना शुरू करें।"),
                    Map.entry("reengage.partner3.title", "SheOut Partner पर लौट आइए"),
                    Map.entry("reengage.partner3.body", "एक महीना हो गया। ऐप खोलें, ऑनलाइन हों और वहीं से शुरू करें जहाँ छोड़ा था।"),
                    Map.entry("reengage.partnerVerify.title", "अपना सत्यापन पूरा करें"),
                    Map.entry("reengage.partnerVerify.body", "बस कुछ कदम और आप SheOut के साथ कमाना शुरू कर सकती हैं। आगे बढ़ने के लिए ऐप खोलें।"),
                    Map.entry("category.BIKE", "बाइक टैक्सी"),
                    Map.entry("category.AUTO", "ऑटो"),
                    Map.entry("category.CAB", "कैब"),
                    Map.entry("category.PARCEL", "पार्सल डिलीवरी"),
                    Map.entry("category.LUNCHBOX", "लंच बॉक्स डिलीवरी")),
            AppLanguage.TE, Map.ofEntries(
                    Map.entry("destinationChangeRequested.title", "మీ రైడర్ గమ్యం మార్చాలనుకుంటున్నారు"),
                    Map.entry("destinationChangeRequested.body", "కొత్త ఛార్జీ {fare} (ఇంతకు ముందు {was}). {minutes} నిమిషాల్లో అంగీకరించడానికి లేదా తిరస్కరించడానికి ట్రిప్ తెరవండి."),
                    Map.entry("destinationChangedRider.title", "గమ్యం మారింది"),
                    Map.entry("destinationChangedRider.body", "మీ పార్ట్‌నర్ అంగీకరించారు. మీ కొత్త ఛార్జీ {fare}."),
                    Map.entry("destinationChangedPartner.title", "గమ్యం మారింది"),
                    Map.entry("destinationChangedPartner.body", "ట్రిప్ ఇప్పుడు కొత్త డ్రాప్‌కు వెళ్తుంది. ఛార్జీ {fare}."),
                    Map.entry("destinationChangeDeclined.title", "మీ పార్ట్‌నర్ కొత్త గమ్యాన్ని తీసుకోలేకపోయారు"),
                    Map.entry("destinationChangeDeclined.body", "మీ ట్రిప్ అసలు డ్రాప్‌కే కొనసాగుతుంది. ఛార్జీ మారలేదు: {fare}."),
                    Map.entry("bookingRequested.title", "బుకింగ్ అభ్యర్థించబడింది"),
                    Map.entry("bookingRequested.body", "మీ దగ్గరలో {category} పార్ట్‌నర్ కోసం వెతుకుతున్నాం."),
                    Map.entry("driverOffer.title", "కొత్త {category} ట్రిప్ అభ్యర్థన"),
                    Map.entry("driverOffer.body", "మీకు {km} కి.మీ దూరంలో. ఆఫర్ {seconds} సెకన్లు ఉంటుంది, అంగీకరించడానికి SheOut తెరవండి."),
                    Map.entry("bookingAccepted.title", "{driver} వస్తున్నారు"),
                    Map.entry("bookingAccepted.body", "ఎక్కే ముందు యాప్‌లో ఆమె ఫోటో మరియు వాహనం నంబర్ చూసుకోండి."),
                    Map.entry("driverArriving.title", "{driver} దగ్గరికి వచ్చేశారు"),
                    Map.entry("driverArriving.body", "ఆమె మీ పికప్‌కు దాదాపు చేరుకున్నారు. మీ పికప్ కోడ్ సిద్ధంగా ఉంచండి."),
                    Map.entry("bookingCompleted.title", "మీరు చేరుకున్నారు: చెల్లింపు బాకీ ఉంది"),
                    Map.entry("bookingCompleted.body", "ఛార్జీ {fare}. ట్రిప్ పూర్తి చేయడానికి యాప్‌లో, మీ SheOut వాలెట్ నుండి లేదా ఆన్‌లైన్‌లో చెల్లించండి."),
                    Map.entry("cancelledByPartner.title", "మీ బుకింగ్ రద్దయింది"),
                    Map.entry("cancelledByPartner.body", "మీ పార్ట్‌నర్ రద్దు చేయాల్సి వచ్చింది. మీకు ఎలాంటి ఛార్జీ పడలేదు, మీకు కావాలనుకున్నప్పుడు మళ్లీ బుక్ చేయండి."),
                    Map.entry("cancelledForRider.title", "మీ బుకింగ్ రద్దయింది"),
                    Map.entry("cancelledForRider.body", "మీకు ఎలాంటి ఛార్జీ పడలేదు, మీకు కావాలనుకున్నప్పుడు మళ్లీ బుక్ చేయండి."),
                    Map.entry("cancelledForPartner.title", "ట్రిప్ రద్దయింది"),
                    Map.entry("cancelledForPartner.body", "రైడర్ ఈ ట్రిప్‌ను రద్దు చేశారు. మీరు పికప్‌కు వెళ్లాల్సిన అవసరం లేదు."),
                    Map.entry("noDrivers.title", "ప్రస్తుతం పార్ట్‌నర్లు అందుబాటులో లేరు"),
                    Map.entry("noDrivers.body", "దగ్గరలో ఎవరూ ఈ ట్రిప్ తీసుకోలేకపోయారు. ఎలాంటి ఛార్జీ పడలేదు, కొన్ని నిమిషాల తర్వాత మళ్లీ ప్రయత్నించండి."),
                    Map.entry("verificationRejected.title", "మీ ID ని ధృవీకరించలేకపోయాం"),
                    Map.entry("verificationRejected.body", "{reason} యాప్‌లో గుర్తింపు ధృవీకరణ నుండి మరో ఫోటో పంపవచ్చు."),
                    Map.entry("profileChangeApproved.title", "మీ ప్రొఫైల్ మార్పు ఆమోదించబడింది"),
                    Map.entry("profileChangeApproved.body", "ఇప్పుడు రైడర్లు మీ కొత్త వివరాలను చూస్తారు."),
                    Map.entry("profileChangeRejected.title", "మీ ప్రొఫైల్ మార్పు ఆమోదించబడలేదు"),
                    Map.entry("profileChangeRejected.body", "కారణం చూడటానికి మీ ప్రొఫైల్ తెరవండి. మీ పాత వివరాలే వాడుకలో ఉన్నాయి."),
                    Map.entry("tripCheckIn.title", "మీ ప్రయాణంలో అంతా బాగానే ఉందా?"),
                    Map.entry("tripCheckIn.body", "మీ ప్రయాణం ఆగిపోయింది లేదా అనుకున్నదానికంటే చాలా ఎక్కువ సమయం పడుతోంది. సహాయం కావాలంటే SheOut తెరిచి SOS నొక్కండి, లేదా 112కి కాల్ చేయండి."),
                    Map.entry("partnerLeft.title", "మీ పార్ట్‌నర్ రద్దు చేయాల్సి వచ్చింది"),
                    Map.entry("partnerLeft.body", "మేము ఇప్పుడే మీకు మరో పార్ట్‌నర్‌ను వెతుకుతున్నాం. మళ్లీ బుక్ చేయాల్సిన అవసరం లేదు."),
                    Map.entry("verifiedPartner.title", "మీ ఖాతా ధృవీకరించబడింది"),
                    Map.entry("verifiedPartner.body", "మీరు ఇప్పుడు ఆన్‌లైన్‌కి వెళ్లి ట్రిప్‌లు అంగీకరించవచ్చు."),
                    Map.entry("verifiedRider.title", "మీ ఖాతా ధృవీకరించబడింది"),
                    Map.entry("verifiedRider.body", "మీరు ఇప్పుడు రైడ్‌లు మరియు డెలివరీలు బుక్ చేయవచ్చు."),
                    Map.entry("supportReply.title", "సపోర్ట్ మీ టికెట్‌కు సమాధానం ఇచ్చింది"),
                    Map.entry("supportReply.body", "సమాధానం చదవడానికి యాప్‌లో సహాయం & సపోర్ట్ తెరవండి."),
                    Map.entry("payoutPaid.title", "పేఅవుట్ పంపబడింది: {amount}"),
                    Map.entry("payoutPaid.body", "SheOut మీ పేఅవుట్ పంపింది. బ్యాంక్ లేదా UPI రిఫరెన్స్: {reference}."),
                    Map.entry("incentive.title", "బోనస్ వచ్చింది: {amount}"),
                    Map.entry("incentive.body", "{name}: మీ ట్రిప్ సంపాదనకు అదనంగా మీ SheOut వాలెట్‌లో జమ చేయబడింది."),
                    Map.entry("referralJoined.title", "మీ కోడ్‌తో ఒక స్నేహితురాలు చేరారు"),
                    Map.entry("referralJoined.body", "ఆమె మొదటి పెయిడ్ రైడ్ తీసుకున్నప్పుడు మీ ఇద్దరికీ {amount} వస్తుంది."),
                    Map.entry("referralJoinedPartner.title", "మీ కోడ్‌తో ఒక పార్ట్‌నర్ చేరారు"),
                    Map.entry("referralJoinedPartner.body", "ఆమె మొదటి పెయిడ్ ట్రిప్ పూర్తి చేసినప్పుడు మీ వాలెట్‌లో {amount} జమ అవుతుంది."),
                    Map.entry("referralJoinedPlain.body", "ఆమెను SheOutకి ఆహ్వానించినందుకు ధన్యవాదాలు."),
                    Map.entry("sellerApproved.title", "మీ SheOut Seller దుకాణం ఆమోదించబడింది"),
                    Map.entry("sellerApproved.body", "లైవ్ కావడానికి ఒక్కసారి {amount} లిస్టింగ్ ఫీజు చెల్లించండి."),
                    Map.entry("sellerRejected.title", "మీ దుకాణంలో కొన్ని మార్పులు కావాలి"),
                    Map.entry("sellerRejected.body", "ఏమి మార్చాలో చూడటానికి SheOut Seller తెరిచి, మళ్ళీ పంపండి."),
                    Map.entry("sellerLive.title", "మీ దుకాణం లైవ్‌లో ఉంది"),
                    Map.entry("sellerLive.body", "ఇప్పుడు కస్టమర్లు మిమ్మల్ని SheOut Sellerలో కనుగొనగలరు."),
                    Map.entry("sellerSuspended.title", "మీ దుకాణం నిలిపివేయబడింది"),
                    Map.entry("sellerSuspended.body", "ప్రస్తుతం ఇది కస్టమర్లకు కనిపించదు. కారణం చూడటానికి SheOut Seller తెరవండి."),
                    Map.entry("referralRewardedReferrer.title", "మీకు {amount} రైడ్ క్రెడిట్ వచ్చింది"),
                    Map.entry("referralRewardedReferrer.body", "మీ స్నేహితురాలు తన మొదటి రైడ్ తీసుకున్నారు. ఈ క్రెడిట్ మీ తదుపరి రైడ్‌లలో వాడబడుతుంది."),
                    Map.entry("referralRewardedReferee.title", "మీ {amount} వెల్‌కమ్ క్రెడిట్ వచ్చింది"),
                    Map.entry("referralRewardedReferee.body", "స్నేహితురాలి కోడ్‌తో చేరినందుకు ధన్యవాదాలు. ఈ క్రెడిట్ మీ తదుపరి రైడ్‌లలో వాడబడుతుంది."),
                    Map.entry("receipt.title", "రసీదు: {amount} చెల్లించబడింది"),
                    Map.entry("receipt.body", "{from} నుండి {to} వరకు మీ {category} ట్రిప్‌కు {when} న {how} చెల్లించారు."),
                    Map.entry("receipt.cash", "మీ పార్ట్‌నర్‌కు నగదుగా"),
                    Map.entry("receipt.sheoutWallet", "మీ SheOut వాలెట్ నుండి"),
                    Map.entry("receipt.promoCredit", "మీ SheOut ప్రమోషనల్ క్రెడిట్ ద్వారా"),
                    Map.entry("receipt.online", "{method} ద్వారా ఆన్‌లైన్‌లో"),
                    Map.entry("method.UPI", "UPI"),
                    Map.entry("method.CARD", "కార్డ్"),
                    Map.entry("method.NETBANKING", "నెట్‌బ్యాంకింగ్"),
                    Map.entry("method.WALLET", "వాలెట్"),
                    Map.entry("method.OTHER", "ఆన్‌లైన్ చెల్లింపు"),
                    Map.entry("yourPartner", "మీ పార్ట్‌నర్"),
                    Map.entry("sosSent.title", "SOS అలర్ట్ పంపబడింది"),
                    Map.entry("sosNoContacts", "మీరు ఎలాంటి అత్యవసర కాంటాక్ట్‌లను సేవ్ చేయలేదు, కాబట్టి ఎవరికీ SMS వెళ్లలేదు. మీ ప్రొఫైల్ నుండి ఒకరిని జోడించండి."),
                    Map.entry("sosNoneReached", "మీ అత్యవసర కాంటాక్ట్‌లలో ఎవరికీ SMS పంపలేకపోయాం. ప్రమాదంలో ఉంటే 112 కి కాల్ చేయండి."),
                    Map.entry("sosSomeReached", "మీ లొకేషన్ {total} మందిలో {notified} అత్యవసర కాంటాక్ట్‌లకు SMS చేయబడింది."),
                    Map.entry("sosOneReached", "మీ లొకేషన్ మీ అత్యవసర కాంటాక్ట్‌కు SMS చేయబడింది."),
                    Map.entry("reengage.rider1.title", "SheOutలో మిమ్మల్ని మిస్ అవుతున్నాం"),
                    Map.entry("reengage.rider1.body", "ఈ రోజు ఎక్కడికైనా వెళ్తున్నారా? కొన్ని ట్యాప్‌లలో ధృవీకరించిన మహిళా పార్ట్‌నర్‌తో రైడ్ బుక్ చేయండి."),
                    Map.entry("reengage.rider2.title", "మీ సురక్షిత రైడ్ ఒక్క ట్యాప్ దూరంలో"),
                    Map.entry("reengage.rider2.body", "\"సురక్షితంగా ప్రయాణించేవారు దూరం వెళ్తారు.\" ఈ రోజే మీ తదుపరి SheOut రైడ్ బుక్ చేయండి."),
                    Map.entry("reengage.rider3.title", "చాలా రోజులైంది!"),
                    Map.entry("reengage.rider3.body", "రైడ్లు, పార్సెల్స్, మీ దగ్గరి మహిళల దుకాణాలు, అన్నీ SheOutలో. కొత్తవి చూడటానికి యాప్ తెరవండి."),
                    Map.entry("reengage.partner1.title", "ఈ రోజు సంపాదించడానికి సిద్ధమా?"),
                    Map.entry("reengage.partner1.body", "మీ దగ్గర రైడర్లు బుక్ చేస్తున్నారు. సిద్ధమైనప్పుడు SheOut Partnerలో ఆన్‌లైన్‌కి రండి."),
                    Map.entry("reengage.partner2.title", "మీ రైడర్లు మిమ్మల్ని మిస్ అవుతున్నారు"),
                    Map.entry("reengage.partner2.body", "\"మీరు ఇచ్చే ప్రతి రైడ్ మరో మహిళను ముందుకు నడిపిస్తుంది.\" ఈ రోజు ఆన్‌లైన్‌కి వచ్చి సంపాదించండి."),
                    Map.entry("reengage.partner3.title", "SheOut Partnerకి తిరిగి రండి"),
                    Map.entry("reengage.partner3.body", "ఒక నెల అయింది. యాప్ తెరిచి, ఆన్‌లైన్‌కి వచ్చి, ఆపిన చోట నుంచి మొదలుపెట్టండి."),
                    Map.entry("reengage.partnerVerify.title", "మీ ధృవీకరణ పూర్తి చేయండి"),
                    Map.entry("reengage.partnerVerify.body", "కొన్ని దశలు చాలు, SheOutతో సంపాదించడం మొదలుపెట్టవచ్చు. కొనసాగించడానికి యాప్ తెరవండి."),
                    Map.entry("category.BIKE", "బైక్ టాక్సీ"),
                    Map.entry("category.AUTO", "ఆటో"),
                    Map.entry("category.CAB", "క్యాబ్"),
                    Map.entry("category.PARCEL", "పార్సెల్ డెలివరీ"),
                    Map.entry("category.LUNCHBOX", "లంచ్ బాక్స్ డెలివరీ"))
    );
}
