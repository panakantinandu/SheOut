# Safety-critical translations - native-speaker review needed

**Status: NOT REVIEWED.** The Telugu and Hindi below were machine-translated as a
starting scaffold. They must be checked by a fluent native speaker before SheOut
ships to real users in those languages.

Why this list exists: these strings are what a woman reads during an SOS, while
reading out or entering the pickup code, or while deciding who her emergency
contacts are. A wrong word here is a safety risk, not a polish issue.

## What the apps do until this is signed off

Every string below is rendered through `SafetyText` / `useSafetyString`. In
Telugu or Hindi it shows the translation **with the English original directly
beneath it**, so nobody is ever left with only an unchecked translation. The SMS
an SOS sends to contacts also carries both languages.

## How to sign a language off

1. A native speaker reviews every row for that language, correcting the files:
   - rider app: `frontend/customer-app/src/i18n/{te,hi}.safety.json`
   - partner app: `frontend/driver-app/src/i18n/{te,hi}.safety.json`
2. Record who reviewed it and when at the bottom of this file.
3. Set that language to `true` in `safetyReviewed` in both apps'
   `src/i18n/index.ts`. From then on the English line is no longer shown.

## Also worth a native read (not safety, but consequential)

- The sign-up consent sentence (`consent.text` in `frontend/design-system/src/i18n/*.json`).
- The account-deletion explanation (`privacy.explain.*` in the same files).
- Legal documents (Privacy Policy, Terms) are **not translated** at all; the apps
  say so in Telugu and Hindi. They need a legal translation, not a machine one.

## Rider app (37 strings)

| Key | English | Telugu | Hindi | Reviewed |
|---|---|---|---|---|
| `raiseIssue.dangerTitle` | Are you in danger right now? | మీరు ఇప్పుడే ప్రమాదంలో ఉన్నారా? | क्या आप अभी ख़तरे में हैं? | ☐ te ☐ hi |
| `raiseIssue.dangerBody` | A ticket is read when support reaches it. If you need help this minute, use SOS to alert your emergency contacts, or call {{number}}. | టికెట్‌ను సపోర్ట్ చేరుకున్నప్పుడు చదువుతారు. మీకు ఈ నిమిషంలోనే సహాయం అవసరమైతే, మీ ఎమర్జెన్సీ కాంటాక్ట్‌లను హెచ్చరించడానికి SOS ఉపయోగించండి, లేదా {{number}}కి కాల్ చేయండి. | टिकट तब पढ़ा जाता है जब सपोर्ट उस तक पहुँचता है। अगर आपको इसी पल मदद चाहिए, तो अपने इमरजेंसी संपर्कों को सूचित करने के लिए SOS इस्तेमाल करें, या {{number}} पर कॉल करें। | ☐ te ☐ hi |
| `contacts.whatSosDoes` | If you raise an SOS, everyone here is sent an SMS with your location. | మీరు SOS నొక్కితే, ఇక్కడ ఉన్న ప్రతి ఒక్కరికీ మీ స్థానంతో SMS పంపబడుతుంది. | अगर आप SOS दबाती हैं, तो यहाँ मौजूद हर व्यक्ति को आपकी लोकेशन के साथ SMS भेजा जाता है। | ☐ te ☐ hi |
| `contacts.tellThem` | They are never messaged at any other time. Please tell them you have added their number - it is their number, not yours, and they have not agreed to anything with us. | వారికి మరే సమయంలోనూ సందేశాలు పంపబడవు. మీరు వారి నంబర్‌ను జోడించారని దయచేసి వారికి చెప్పండి - అది వారి నంబర్, మీది కాదు, వారు మాతో దేనికీ అంగీకరించలేదు. | उन्हें किसी और समय कभी संदेश नहीं भेजा जाता। कृपया उन्हें बताएँ कि आपने उनका नंबर जोड़ा है - यह उनका नंबर है, आपका नहीं, और उन्होंने हमसे किसी बात पर सहमति नहीं दी है। | ☐ te ☐ hi |
| `contacts.emptyWarning` | SOS cannot reach anyone until you add at least one person. | మీరు కనీసం ఒక వ్యక్తిని జోడించే వరకు SOS ఎవరినీ చేరుకోలేదు. | जब तक आप कम से कम एक व्यक्ति नहीं जोड़तीं, SOS किसी तक नहीं पहुँच सकता। | ☐ te ☐ hi |
| `contacts.removeWarning` | {{name}} will no longer be told if you raise an SOS. | మీరు SOS నొక్కితే ఇకపై {{name}}కి తెలియజేయబడదు. | अगर आप SOS दबाती हैं, तो {{name}} को अब सूचित नहीं किया जाएगा। | ☐ te ☐ hi |
| `sos.noGeolocation` | This browser cannot read your location. | ఈ బ్రౌజర్ మీ స్థానాన్ని చదవలేదు. | यह ब्राउज़र आपकी लोकेशन नहीं पढ़ सकता। | ☐ te ☐ hi |
| `sos.locationDenied` | Could not get your location - allow location access and try again. | మీ స్థానం దొరకలేదు - లొకేషన్ యాక్సెస్ అనుమతించి మళ్లీ ప్రయత్నించండి. | आपकी लोकेशन नहीं मिल सकी - लोकेशन की अनुमति दें और फिर कोशिश करें। | ☐ te ☐ hi |
| `sos.noLocation` | Could not get your location. | మీ స్థానం దొరకలేదు. | आपकी लोकेशन नहीं मिल सकी। | ☐ te ☐ hi |
| `sos.ifInDanger` | If you are in danger, call {{number}}. | మీరు ప్రమాదంలో ఉంటే, {{number}}కి కాల్ చేయండి. | अगर आप ख़तरे में हैं, तो {{number}} पर कॉल करें। | ☐ te ☐ hi |
| `sos.cannotReachSheout` | Could not reach SheOut. Text your contacts from your phone below, or call {{number}}. | SheOutను చేరుకోలేకపోయాం. క్రింద మీ ఫోన్ నుండి మీ కాంటాక్ట్‌లకు సందేశం పంపండి, లేదా {{number}}కి కాల్ చేయండి. | SheOut तक नहीं पहुँच सके। नीचे अपने फ़ोन से अपने संपर्कों को मैसेज भेजें, या {{number}} पर कॉल करें। | ☐ te ☐ hi |
| `sos.smsNamed` | SOS - {{name}} needs help. My location: {{link}} (sent from SheOut) | SOS - {{name}}కి సహాయం కావాలి. నా స్థానం: {{link}} (SheOut నుండి పంపబడింది) | SOS - {{name}} को मदद चाहिए। मेरी लोकेशन: {{link}} (SheOut से भेजा गया) | ☐ te ☐ hi |
| `sos.smsUnnamed` | SOS - I need help. My location: {{link}} (sent from SheOut) | SOS - నాకు సహాయం కావాలి. నా స్థానం: {{link}} (SheOut నుండి పంపబడింది) | SOS - मुझे मदद चाहिए। मेरी लोकेशन: {{link}} (SheOut से भेजा गया) | ☐ te ☐ hi |
| `sos.noContactsSaved` | No emergency contacts saved yet. Tap Safety Features below to add one. | ఇంకా ఎమర్జెన్సీ కాంటాక్ట్‌లు సేవ్ చేయలేదు. ఒకరిని జోడించడానికి క్రింద భద్రతా ఫీచర్లు నొక్కండి. | अभी कोई इमरजेंसी संपर्क सेव नहीं है। एक जोड़ने के लिए नीचे सुरक्षा सुविधाएँ पर टैप करें। | ☐ te ☐ hi |
| `sos.resultNoContacts` | Your alert is recorded with SheOut, but you have no emergency contacts saved, so nobody was texted. Share your location below or call {{number}}. | మీ హెచ్చరిక SheOutలో నమోదైంది, కానీ మీకు ఎమర్జెన్సీ కాంటాక్ట్‌లు సేవ్ చేయలేదు, కాబట్టి ఎవరికీ సందేశం వెళ్లలేదు. క్రింద మీ స్థానాన్ని పంచుకోండి లేదా {{number}}కి కాల్ చేయండి. | आपका अलर्ट SheOut में दर्ज हो गया है, लेकिन आपके कोई इमरजेंसी संपर्क सेव नहीं हैं, इसलिए किसी को मैसेज नहीं गया। नीचे अपनी लोकेशन साझा करें या {{number}} पर कॉल करें। | ☐ te ☐ hi |
| `sos.resultRecent` | Alert recorded with your latest location. Your contacts were texted less than a minute ago. Call {{number}} if you need help right now. | మీ తాజా స్థానంతో హెచ్చరిక నమోదైంది. ఒక నిమిషం లోపే మీ కాంటాక్ట్‌లకు సందేశం వెళ్లింది. మీకు ఇప్పుడే సహాయం అవసరమైతే {{number}}కి కాల్ చేయండి. | आपकी ताज़ा लोकेशन के साथ अलर्ट दर्ज हो गया। आपके संपर्कों को एक मिनट से कम पहले मैसेज भेजा गया था। अगर आपको अभी मदद चाहिए, तो {{number}} पर कॉल करें। | ☐ te ☐ hi |
| `sos.resultNoneReached_one` | Your alert is recorded with SheOut, but our text to your contact didn't go through. Send it from your own phone now - it takes one tap. | మీ హెచ్చరిక SheOutలో నమోదైంది, కానీ మీ కాంటాక్ట్‌కు మా సందేశం చేరలేదు. ఇప్పుడే మీ స్వంత ఫోన్ నుండి పంపండి - ఒక్క నొక్కు చాలు. | आपका अलर्ट SheOut में दर्ज हो गया है, लेकिन आपके संपर्क को हमारा मैसेज नहीं पहुँचा। अभी अपने फ़ोन से भेजें - बस एक टैप। | ☐ te ☐ hi |
| `sos.resultNoneReached_other` | Your alert is recorded with SheOut, but our text to your {{count}} contacts didn't go through. Send it from your own phone now - it takes one tap. | మీ హెచ్చరిక SheOutలో నమోదైంది, కానీ మీ {{count}} కాంటాక్ట్‌లకు మా సందేశం చేరలేదు. ఇప్పుడే మీ స్వంత ఫోన్ నుండి పంపండి - ఒక్క నొక్కు చాలు. | आपका अलर्ट SheOut में दर्ज हो गया है, लेकिन आपके {{count}} संपर्कों को हमारा मैसेज नहीं पहुँचा। अभी अपने फ़ोन से भेजें - बस एक टैप। | ☐ te ☐ hi |
| `sos.resultSomeReached` | Texted {{notified}} of {{total}} contacts. Send it from your phone to reach everyone. | {{total}} కాంటాక్ట్‌లలో {{notified}} మందికి సందేశం వెళ్లింది. అందరినీ చేరుకోవడానికి మీ ఫోన్ నుండి పంపండి. | {{total}} में से {{notified}} संपर्कों को मैसेज गया। सब तक पहुँचने के लिए अपने फ़ोन से भेजें। | ☐ te ☐ hi |
| `sos.resultAllReached_one` | Your location was texted to your emergency contact. | మీ స్థానం మీ ఎమర్జెన్సీ కాంటాక్ట్‌కు సందేశంగా వెళ్లింది. | आपकी लोकेशन आपके इमरजेंसी संपर्क को मैसेज कर दी गई। | ☐ te ☐ hi |
| `sos.resultAllReached_other` | Your location was texted to all {{count}} emergency contacts. | మీ స్థానం మొత్తం {{count}} ఎమర్జెన్సీ కాంటాక్ట్‌లకు సందేశంగా వెళ్లింది. | आपकी लोकेशन सभी {{count}} इमरजेंसी संपर्कों को मैसेज कर दी गई। | ☐ te ☐ hi |
| `sos.inEmergency` | In Emergency? | ఎమర్జెన్సీలో ఉన్నారా? | इमरजेंसी में हैं? | ☐ te ☐ hi |
| `sos.sending` | Sending SOS alert... | SOS హెచ్చరిక పంపుతోంది... | SOS अलर्ट भेजा जा रहा है... | ☐ te ☐ hi |
| `sos.pressToAlert` | Press SOS to alert your emergency contacts with your location | మీ స్థానంతో మీ ఎమర్జెన్సీ కాంటాక్ట్‌లను హెచ్చరించడానికి SOS నొక్కండి | अपनी लोकेशन के साथ अपने इमरजेंसी संपर्कों को सूचित करने के लिए SOS दबाएँ | ☐ te ☐ hi |
| `sos.textOneFromPhone` | Text {{name}} from your phone | మీ ఫోన్ నుండి {{name}}కి సందేశం పంపండి | अपने फ़ोन से {{name}} को मैसेज भेजें | ☐ te ☐ hi |
| `sos.textAllFromPhone` | Text all {{count}} contacts from your phone | మీ ఫోన్ నుండి మొత్తం {{count}} కాంటాక్ట్‌లకు సందేశం పంపండి | अपने फ़ोन से सभी {{count}} संपर्कों को मैसेज भेजें | ☐ te ☐ hi |
| `sos.shareAnotherWay` | Share location another way | స్థానాన్ని మరో విధంగా పంచుకోండి | लोकेशन किसी और तरीके से साझा करें | ☐ te ☐ hi |
| `sos.callNumber` | Call {{number}} | {{number}}కి కాల్ చేయండి | {{number}} पर कॉल करें | ☐ te ☐ hi |
| `sos.gettingLocation` | Getting location... | స్థానం తెలుసుకుంటోంది... | लोकेशन ली जा रही है... | ☐ te ☐ hi |
| `sos.shareLocation` | Share Location | స్థానాన్ని పంచుకోండి | लोकेशन साझा करें | ☐ te ☐ hi |
| `sos.callContact` | Call Contact | కాంటాక్ట్‌కు కాల్ చేయండి | संपर्क को कॉल करें | ☐ te ☐ hi |
| `sos.noContactsYet` | You have no emergency contacts yet - add at least one. | మీకు ఇంకా ఎమర్జెన్సీ కాంటాక్ట్‌లు లేవు - కనీసం ఒకరిని జోడించండి. | आपके अभी कोई इमरजेंसी संपर्क नहीं हैं - कम से कम एक जोड़ें। | ☐ te ☐ hi |
| `sos.alertsGoTo` | SOS alerts go to {{names}}. | SOS హెచ్చరికలు {{names}}కి వెళ్తాయి. | SOS अलर्ट {{names}} को जाते हैं। | ☐ te ☐ hi |
| `pickupCode.haveReady` | Watch her approach on the map. Have your code ready to read out. | మ్యాప్‌లో ఆమె దగ్గరకు రావడం చూడండి. చదివి చెప్పడానికి మీ కోడ్ సిద్ధంగా ఉంచుకోండి. | मैप पर उसे आते हुए देखें। पढ़कर बताने के लिए अपना कोड तैयार रखें। | ☐ te ☐ hi |
| `pickupCode.title` | Your pickup code | మీ పికప్ కోడ్ | आपका पिकअप कोड | ☐ te ☐ hi |
| `pickupCode.instruction` | Read this out to your partner before you get in. She cannot start the trip without it. | వాహనం ఎక్కే ముందు దీన్ని మీ పార్ట్‌నర్‌కు చదివి చెప్పండి. ఇది లేకుండా ఆమె ట్రిప్ ప్రారంభించలేరు. | बैठने से पहले इसे अपनी पार्टनर को पढ़कर बताएँ। इसके बिना वह ट्रिप शुरू नहीं कर सकती। | ☐ te ☐ hi |
| `pickupCode.aria` | Your pickup code is {{digits}} | మీ పికప్ కోడ్ {{digits}} | आपका पिकअप कोड {{digits}} है | ☐ te ☐ hi |

## Partner app (7 strings)

| Key | English | Telugu | Hindi | Reviewed |
|---|---|---|---|---|
| `raiseIssue.dangerTitle` | Are you in danger right now? | మీరు ఇప్పుడే ప్రమాదంలో ఉన్నారా? | क्या आप अभी ख़तरे में हैं? | ☐ te ☐ hi |
| `raiseIssue.dangerBody` | A ticket is read when support reaches it. If you need help this minute, stop somewhere safe and call {{number}}, or call driver support from the Help screen. | టికెట్‌ను సపోర్ట్ చేరుకున్నప్పుడు చదువుతారు. మీకు ఈ నిమిషంలోనే సహాయం అవసరమైతే, సురక్షితమైన చోట ఆగి {{number}}కి కాల్ చేయండి, లేదా సహాయం స్క్రీన్ నుండి డ్రైవర్ సపోర్ట్‌కు కాల్ చేయండి. | टिकट तब पढ़ा जाता है जब सपोर्ट उस तक पहुँचता है। अगर आपको इसी पल मदद चाहिए, तो किसी सुरक्षित जगह रुककर {{number}} पर कॉल करें, या सहायता स्क्रीन से ड्राइवर सपोर्ट को कॉल करें। | ☐ te ☐ hi |
| `raiseIssue.call` | Call {{number}} | {{number}}కి కాల్ చేయండి | {{number}} पर कॉल करें | ☐ te ☐ hi |
| `pickupCode.locked` | This trip needs support to sort out before it can start. | ఈ ట్రిప్ ప్రారంభమయ్యే ముందు సపోర్ట్ దీన్ని పరిష్కరించాలి. | इस ट्रिप के शुरू होने से पहले सपोर्ट को इसे सुलझाना होगा। | ☐ te ☐ hi |
| `pickupCode.askForCode` | Ask your rider for her four-digit code and enter it here. The trip starts once it matches. | మీ రైడర్‌ను ఆమె నాలుగు అంకెల కోడ్ అడిగి ఇక్కడ నమోదు చేయండి. కోడ్ సరిపోలగానే ట్రిప్ ప్రారంభమవుతుంది. | अपनी राइडर से उनका चार अंकों का कोड पूछें और यहाँ डालें। कोड मिलते ही ट्रिप शुरू हो जाती है। | ☐ te ☐ hi |
| `pickupCode.fieldLabel` | Pickup code | పికప్ కోడ్ | पिकअप कोड | ☐ te ☐ hi |
| `pickupCode.fieldPlaceholder` | 4-digit code | 4 అంకెల కోడ్ | 4 अंकों का कोड | ☐ te ☐ hi |

## Sign-off

| Language | Reviewer | Date |
|---|---|---|
| Telugu | | |
| Hindi | | |
