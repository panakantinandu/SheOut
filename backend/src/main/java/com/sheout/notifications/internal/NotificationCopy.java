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
                    Map.entry("bookingRequested.title", "Booking requested"),
                    Map.entry("bookingRequested.body", "We're finding a {category} partner near you."),
                    Map.entry("driverOffer.title", "New {category} trip request"),
                    Map.entry("driverOffer.body", "{km} km from you. Offers last {seconds} seconds - open SheOut to accept."),
                    Map.entry("bookingAccepted.title", "{driver} is on the way"),
                    Map.entry("bookingAccepted.body", "Check her photo and vehicle number in the app before you get in."),
                    Map.entry("driverArriving.title", "{driver} is arriving"),
                    Map.entry("driverArriving.body", "She is almost at your pickup. Have your pickup code ready."),
                    Map.entry("bookingCompleted.title", "You have arrived - payment due"),
                    Map.entry("bookingCompleted.body", "Fare {fare}. Pay in the app, from your SheOut wallet or online, to finish the trip."),
                    Map.entry("cancelledByPartner.title", "Your booking was cancelled"),
                    Map.entry("cancelledByPartner.body", "Your partner had to cancel. You have not been charged - book again whenever you are ready."),
                    Map.entry("cancelledForRider.title", "Your booking was cancelled"),
                    Map.entry("cancelledForRider.body", "You have not been charged - book again whenever you are ready."),
                    Map.entry("cancelledForPartner.title", "Trip cancelled"),
                    Map.entry("cancelledForPartner.body", "The rider cancelled this trip. You do not need to go to the pickup."),
                    Map.entry("noDrivers.title", "No partners available right now"),
                    Map.entry("noDrivers.body", "Nobody nearby could take this trip. Nothing was charged - try again in a few minutes."),
                    Map.entry("verificationRejected.title", "We could not verify your ID"),
                    Map.entry("verificationRejected.body", "{reason} You can send another photo from Identity Verification in the app."),
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
                    Map.entry("category.BIKE", "bike taxi"),
                    Map.entry("category.AUTO", "auto"),
                    Map.entry("category.CAB", "cab"),
                    Map.entry("category.PARCEL", "parcel delivery"),
                    Map.entry("category.LUNCHBOX", "lunch box delivery")),
            AppLanguage.HI, Map.ofEntries(
                    Map.entry("bookingRequested.title", "बुकिंग का अनुरोध किया गया"),
                    Map.entry("bookingRequested.body", "हम आपके पास एक {category} पार्टनर ढूँढ रहे हैं।"),
                    Map.entry("driverOffer.title", "नया {category} ट्रिप अनुरोध"),
                    Map.entry("driverOffer.body", "आपसे {km} किमी दूर। ऑफ़र {seconds} सेकंड तक रहता है - स्वीकार करने के लिए SheOut खोलें।"),
                    Map.entry("bookingAccepted.title", "{driver} आ रही हैं"),
                    Map.entry("bookingAccepted.body", "बैठने से पहले ऐप में उनकी फ़ोटो और वाहन नंबर देख लें।"),
                    Map.entry("driverArriving.title", "{driver} पहुँचने वाली हैं"),
                    Map.entry("driverArriving.body", "वे आपके पिकअप के लगभग पास हैं। अपना पिकअप कोड तैयार रखें।"),
                    Map.entry("bookingCompleted.title", "आप पहुँच गई हैं - भुगतान बाकी है"),
                    Map.entry("bookingCompleted.body", "किराया {fare}। ट्रिप पूरी करने के लिए ऐप में, अपने SheOut वॉलेट से या ऑनलाइन भुगतान करें।"),
                    Map.entry("cancelledByPartner.title", "आपकी बुकिंग रद्द हो गई"),
                    Map.entry("cancelledByPartner.body", "आपकी पार्टनर को रद्द करना पड़ा। आपसे कोई शुल्क नहीं लिया गया - जब चाहें फिर से बुक करें।"),
                    Map.entry("cancelledForRider.title", "आपकी बुकिंग रद्द हो गई"),
                    Map.entry("cancelledForRider.body", "आपसे कोई शुल्क नहीं लिया गया - जब चाहें फिर से बुक करें।"),
                    Map.entry("cancelledForPartner.title", "ट्रिप रद्द हो गई"),
                    Map.entry("cancelledForPartner.body", "राइडर ने यह ट्रिप रद्द कर दी। आपको पिकअप पर जाने की ज़रूरत नहीं है।"),
                    Map.entry("noDrivers.title", "अभी कोई पार्टनर उपलब्ध नहीं है"),
                    Map.entry("noDrivers.body", "पास में कोई यह ट्रिप नहीं ले सका। कोई शुल्क नहीं लिया गया - कुछ मिनट बाद फिर कोशिश करें।"),
                    Map.entry("verificationRejected.title", "हम आपकी ID सत्यापित नहीं कर सके"),
                    Map.entry("verificationRejected.body", "{reason} आप ऐप में पहचान सत्यापन से दूसरी फ़ोटो भेज सकती हैं।"),
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
                    Map.entry("category.BIKE", "बाइक टैक्सी"),
                    Map.entry("category.AUTO", "ऑटो"),
                    Map.entry("category.CAB", "कैब"),
                    Map.entry("category.PARCEL", "पार्सल डिलीवरी"),
                    Map.entry("category.LUNCHBOX", "लंच बॉक्स डिलीवरी")),
            AppLanguage.TE, Map.ofEntries(
                    Map.entry("bookingRequested.title", "బుకింగ్ అభ్యర్థించబడింది"),
                    Map.entry("bookingRequested.body", "మీ దగ్గరలో {category} పార్ట్‌నర్ కోసం వెతుకుతున్నాం."),
                    Map.entry("driverOffer.title", "కొత్త {category} ట్రిప్ అభ్యర్థన"),
                    Map.entry("driverOffer.body", "మీకు {km} కి.మీ దూరంలో. ఆఫర్ {seconds} సెకన్లు ఉంటుంది - అంగీకరించడానికి SheOut తెరవండి."),
                    Map.entry("bookingAccepted.title", "{driver} వస్తున్నారు"),
                    Map.entry("bookingAccepted.body", "ఎక్కే ముందు యాప్‌లో ఆమె ఫోటో మరియు వాహనం నంబర్ చూసుకోండి."),
                    Map.entry("driverArriving.title", "{driver} దగ్గరికి వచ్చేశారు"),
                    Map.entry("driverArriving.body", "ఆమె మీ పికప్‌కు దాదాపు చేరుకున్నారు. మీ పికప్ కోడ్ సిద్ధంగా ఉంచండి."),
                    Map.entry("bookingCompleted.title", "మీరు చేరుకున్నారు - చెల్లింపు బాకీ ఉంది"),
                    Map.entry("bookingCompleted.body", "ఛార్జీ {fare}. ట్రిప్ పూర్తి చేయడానికి యాప్‌లో, మీ SheOut వాలెట్ నుండి లేదా ఆన్‌లైన్‌లో చెల్లించండి."),
                    Map.entry("cancelledByPartner.title", "మీ బుకింగ్ రద్దయింది"),
                    Map.entry("cancelledByPartner.body", "మీ పార్ట్‌నర్ రద్దు చేయాల్సి వచ్చింది. మీకు ఎలాంటి ఛార్జీ పడలేదు - మీకు కావాలనుకున్నప్పుడు మళ్లీ బుక్ చేయండి."),
                    Map.entry("cancelledForRider.title", "మీ బుకింగ్ రద్దయింది"),
                    Map.entry("cancelledForRider.body", "మీకు ఎలాంటి ఛార్జీ పడలేదు - మీకు కావాలనుకున్నప్పుడు మళ్లీ బుక్ చేయండి."),
                    Map.entry("cancelledForPartner.title", "ట్రిప్ రద్దయింది"),
                    Map.entry("cancelledForPartner.body", "రైడర్ ఈ ట్రిప్‌ను రద్దు చేశారు. మీరు పికప్‌కు వెళ్లాల్సిన అవసరం లేదు."),
                    Map.entry("noDrivers.title", "ప్రస్తుతం పార్ట్‌నర్లు అందుబాటులో లేరు"),
                    Map.entry("noDrivers.body", "దగ్గరలో ఎవరూ ఈ ట్రిప్ తీసుకోలేకపోయారు. ఎలాంటి ఛార్జీ పడలేదు - కొన్ని నిమిషాల తర్వాత మళ్లీ ప్రయత్నించండి."),
                    Map.entry("verificationRejected.title", "మీ ID ని ధృవీకరించలేకపోయాం"),
                    Map.entry("verificationRejected.body", "{reason} యాప్‌లో గుర్తింపు ధృవీకరణ నుండి మరో ఫోటో పంపవచ్చు."),
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
                    Map.entry("category.BIKE", "బైక్ టాక్సీ"),
                    Map.entry("category.AUTO", "ఆటో"),
                    Map.entry("category.CAB", "క్యాబ్"),
                    Map.entry("category.PARCEL", "పార్సెల్ డెలివరీ"),
                    Map.entry("category.LUNCHBOX", "లంచ్ బాక్స్ డెలివరీ"))
    );
}
