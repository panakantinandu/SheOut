/**
 * DRAFT, pending legal review. Every document in this file.
 * <p>
 * The legal pages both apps and the public site show. Written against what
 * this system actually does, not from a template: docs/legal-audit.md is the
 * inventory every claim here was checked against - the fields the API
 * really stores, the third parties requests really reach, who can really
 * see an identity document, how long things are really kept. A policy that
 * describes a different product is worse than none, because it is a promise
 * nobody kept.
 * <p>
 * Business details (name, CIN, address, grievance officer) come from
 * legal-entity.config.json through entity.ts, never from this file. A value
 * the owner has not supplied reads as a visible placeholder.
 * <p>
 * NOT LEGAL ADVICE. Wording a lawyer has not approved is either marked
 * {@link TODO_LAWYER} in the text or listed in docs/legal-open-items.md.
 */
import { GRIEVANCE_REGIMES, LEGAL_ENTITY, TODO_LEGAL, entityValue, grievanceLine, grievanceOfficer, isProcessorLive, type ProcessorKey } from './entity';

export { TODO_LEGAL, TODO_FROM_OWNER, LEGAL_ENTITY, entityValue, isProcessorLive, missingEntityValues, grievanceOfficer, grievanceLine } from './entity';

export interface LegalSection {
  heading: string;
  paragraphs: string[];
  /** Rendered as a list under the paragraphs. */
  bullets?: string[];
}

export interface LegalDocument {
  title: string;
  /** Shown under the title, so a reader knows which version they agreed to. */
  effective: string;
  intro: string;
  sections: LegalSection[];
}

/**
 * The version of the Terms and Privacy Policy people agree to at sign-up.
 * The server records the same value (sheout.legal.terms-version) - the two
 * are checked against each other in CI (scripts/check-legal-sync.mjs).
 * Bumping it does not by itself ask anyone to agree again: the server
 * records acceptance once, at sign-up. Bump it when the substance changes.
 */
export const LEGAL_VERSION = '2026-10-02';

/** When the wording of these pages last changed, shown on each of them. */
export const LEGAL_UPDATED = '14 October 2026';

const effective = (version: string = LEGAL_VERSION) => `Version ${version} · Last updated ${LEGAL_UPDATED}`;

const E = LEGAL_ENTITY;
// One officer per grievance regime (legal-entity.config.json), the appointment in effect today.
const consumerOfficer = grievanceLine('consumerEcommerce');
const dataOfficer = grievanceLine('dataProtection');

/** Wording a lawyer has not yet approved. Rendered visibly, like TODO_LEGAL. */
export const TODO_LAWYER = '[to be reviewed by lawyer]';

/**
 * Who else receives personal data, one line per processor, named only while
 * legal-entity.config.json marks it live. A processor switched on later is
 * a config change, and the policy follows it.
 */
const PROCESSOR_LINES: Record<ProcessorKey, string> = {
  render: `Render (Render Services, Inc.), our hosting provider. Our servers, our database - including uploaded identity documents, selfies and parcel photos - and our cache run on Render's servers in ${E.dataLocation}. This means your data is stored outside India.`,
  vercel: `Vercel, which serves the app's pages to your browser from servers around the world. It receives your IP address and the pages you ask for, as any website host does. It does not receive your account details.`,
  googleMaps: `Google (Google Maps and Places), for the map and the address search. Receives your IP address, the part of the map you are looking at, and what you type into the search. Not your name or phone number.`,
  googleFonts: `Google Fonts, which supplies the app's typefaces. Your browser fetches them from Google, so Google receives your IP address.`,
  googleSignIn: `Google, only if you choose "Continue with Google". We receive your name and email address from Google.`,
  firebase: `Google Firebase Cloud Messaging, only if you allow notifications. Receives a device token and the text of each notification we send you.`,
  razorpay: `Razorpay, for payments made in the app. Receives the amount and the payment details you enter on its screen. Card and UPI details stay with Razorpay; SheOut never receives them.`,
  aws: `Amazon Web Services (Mumbai, India), for partners only. At the start of a shift, your new selfie and your verified selfie are compared there to confirm it is you. AWS returns a similarity score and does not keep the photos.`,
  nominatim: `OpenStreetMap's Nominatim service, to turn a pin on the map into a street address. Receives the coordinates, not who you are.`,
  anthropic: `Anthropic, only if you use the help chat or "search in your own words" in the Marketplace. Receives what you type there (and, for search, public listings). Not your name, phone number or account details.`,
  sentry: `Sentry, which tells us when the app or server has an error. Phone numbers are removed from error reports before they are sent.`,
  sms: `Our SMS provider, for sign-in codes and SOS texts. Receives the phone number and the message.`,
};

const liveProcessors = (Object.keys(PROCESSOR_LINES) as ProcessorKey[])
  .filter(isProcessorLive)
  .map((key) => PROCESSOR_LINES[key]);

const whoWeAre = (): LegalSection => ({
  heading: 'Who we are',
  paragraphs: [
    `${E.brandLine}. Registered name: ${E.legalName}. CIN: ${entityValue(E.cin)}. Registered office: ${entityValue(E.registeredAddress)}.`,
    `Contact: ${E.supportEmail}, ${entityValue(E.supportPhone)}. Grievance Officer: ${consumerOfficer}. Personal data: ${dataOfficer}.`,
  ],
});

/** DRAFT, pending legal review. */
export const PRIVACY_POLICY: LegalDocument = {
  title: 'Privacy Policy',
  effective: effective(),
  intro: `SheOut is a ride and delivery service for women in Hyderabad. ${E.brandLine}. Running it safely means we ask for more than a taxi app normally would, including a government ID and your location. This page says what we take, why, who sees it, where it is kept, and what you can ask us to do.`,
  sections: [
    {
      heading: 'What we collect, and why',
      paragraphs: ['Each item below is here because a part of the service does not work without it.'],
      bullets: [
        'Your mobile number. It is how you sign in (a one-time code, no password) and how the other person on a trip can reach you.',
        'Your name and date of birth. Your name is shown to the person you travel with. Your date of birth confirms you are 18 or over.',
        'Your email address, if you give it. Optional for customers. Used for receipts and account messages.',
        'A government ID photo and a live selfie. A person on our team checks them to confirm who you are. You can cover your Aadhaar number before sending; we do not store ID numbers typed in by you for this check.',
        'Your location. A customer\'s location is read when she sets a pickup or raises an SOS. A partner\'s location is shared while she is online, so nearby customers can be matched and can watch her arrive. Nothing is read while the app is closed.',
        'Addresses you search for and save.',
        'Your trips and payments: pickup, drop, times, fare and how it was paid.',
        'Messages you send in trip chat, ratings you give, and support requests.',
        'Your emergency contacts: names and numbers you enter, used only when you raise an SOS.',
        'For parcels: the receiver\'s name and phone number, and photos the partner takes at pickup and handover.',
        'For partners: your driving licence, vehicle registration (RC), insurance and PUC certificates and their numbers, your vehicle number, any police or background certificate you upload, a selfie, a short set of liveness frames and a photo of your helmet at the start of each shift, and the bank account or UPI ID your earnings are paid to.',
        'For partners, only if SheOut asks and you agree separately: a record of our manual search of public court records for your name (where we searched, under which name, what we found). It is a screening, not police verification. If you are not cleared, the details are anonymised after 30 days; when you leave, after 12 months.',
        'For sellers in the Marketplace: your shop details, and the phone or WhatsApp number you choose to show to signed-in customers.',
        'Technical details: a notification token if you allow notifications, the type of browser you sign in from (shown to you under "Your devices"), and a random install ID used to stop referral fraud.',
      ],
    },
    {
      heading: 'Face matching',
      paragraphs: [
        'When you verify, a person compares your selfie with your ID photo. Partners also take a selfie at the start of each shift. The phone compares it with the verified selfie, and the comparison may also be checked by Amazon Web Services in Mumbai. We keep the selfies and the similarity score. We do not keep a face template, and the comparison is used for nothing else.',
        `Your verification selfie is kept while your account is open and deleted with it. Start-of-shift photos (the selfie, the liveness frames and the helmet photo) are deleted once they are 30 days old, when you next start a shift; the result of each check is kept. ${TODO_LAWYER}`,
      ],
    },
    {
      heading: 'What we do not collect',
      paragraphs: ['Stated plainly so you can hold us to it.'],
      bullets: [
        'No passwords.',
        'No card numbers or UPI IDs of customers. Payments are handled on Razorpay\'s screen.',
        'No access to your contacts, photo library or microphone.',
        'No location while the app is closed.',
        'No advertising, no analytics trackers, and no selling of your data to anyone.',
      ],
    },
    {
      heading: 'Who can see your documents',
      paragraphs: [
        'Your ID, selfies and partner documents are seen only by the members of our team who review them, through links that expire after a few minutes. Every time a document is opened, we record who opened it and when. Customers and partners never see each other\'s documents.',
        'Verification is a human decision. No automated system approves or rejects you.',
      ],
    },
    {
      heading: 'About your emergency contacts',
      paragraphs: [
        'When you add an emergency contact you give us the number of someone who has not agreed to anything with us. We use it for one purpose only: if you raise an SOS, that person is sent a message saying you raised an alert and where you were. Today the app opens your own SMS app with that message ready to send; SheOut will send the message itself once its SMS service is approved. We never contact them at any other time.',
        'Please tell the people you add. If a contact asks us to remove their number, we will, and we will tell you.',
      ],
    },
    {
      heading: 'Who else receives your data',
      paragraphs: [
        'These are the companies that receive personal data to run SheOut. Each one acts on our instructions. We do not sell data to anyone.',
      ],
      bullets: liveProcessors,
    },
    {
      heading: 'Where your data is stored',
      paragraphs: [
        `Our database and uploaded files are stored on servers in ${E.dataLocation}, so your data is transferred outside India. Indian law allows this. Selfie comparisons happen in Mumbai, India. App pages are served from servers around the world. ${TODO_LAWYER}`,
      ],
    },
    {
      heading: 'How long we keep things',
      paragraphs: [
        'Sign-in codes last a few minutes. A signed-in session lasts up to 30 days.',
        `Parcel photos and the receiver's phone number are deleted 90 days after the delivery, unless a dispute about it is still open.`,
        'Everything else on this page is kept while your account is open and deleted or anonymised when you delete your account. Two exceptions: trip and payment records are kept as long as tax and accounting law requires, and records about a safety report or dispute are kept until it is resolved.',
        'Start-of-shift photos are deleted once they are 30 days old, when the partner next starts a shift.',
        `We are setting shorter periods for chat messages, location history and the images of rejected documents, and will update this page when they start. ${TODO_LAWYER}`,
      ],
    },
    {
      heading: 'Keeping it safe',
      paragraphs: [
        'Data is encrypted between the apps and our servers. Our team signs in to a separate console with strong authentication, and each person sees only what their role needs. Access to documents is logged.',
        'No system is unbreachable. If your personal data is exposed, we will tell you without delay what happened, what it means for you and what we are doing, and we will report it to the Data Protection Board of India as the law requires.',
      ],
    },
    {
      heading: 'Your rights',
      paragraphs: [
        'You can ask for a copy of your data, correct it, delete your account, withdraw a consent, and name someone to act for you if you cannot.',
        'In the app, under Profile, Privacy & Data: "Download my data" gives you a file of what we hold about your account, and "Delete my account" closes it at once. You can also delete your account from the "Delete your account" page on our website. Completed trips and payments stay for the period the law requires, showing you as "Deleted User".',
        'Withdrawing consent for identity checks means we can no longer offer you the service, because every customer and partner on SheOut is verified.',
        `For anything else, write to the Grievance Officer for personal data: ${dataOfficer}. We acknowledge within ${E.grievanceAcknowledgeHours} hours and answer within ${E.grievanceResolveDays} days. If you are not satisfied, you can complain to the Data Protection Board of India.`,
      ],
    },
    {
      heading: 'Children',
      paragraphs: [
        'SheOut is only for people aged 18 or over. The app checks your date of birth, and we close any account we learn belongs to someone younger.',
      ],
    },
    {
      heading: 'Changes to this policy',
      paragraphs: [
        'If we change what we collect or why, we will show you the new version in the app. The version number at the top tells you which one you agreed to.',
      ],
    },
    whoWeAre(),
  ],
};

/** DRAFT, pending legal review. */
export const TERMS_OF_SERVICE: LegalDocument = {
  title: 'Terms of Use',
  effective: effective(),
  intro: `These terms are the agreement between you and ${E.legalName}, which runs SheOut. They cover customers. Partners have their own Partner Terms.`,
  sections: [
    {
      heading: 'What SheOut is',
      paragraphs: [
        'SheOut is an app that connects customers with independent partners for rides and parcel deliveries in Hyderabad. We match you, set the fare and handle payment.',
        `Partners are not our employees. What we take responsibility for is who we let onto the platform and what we do when you tell us something went wrong. ${TODO_LAWYER}`,
      ],
    },
    {
      heading: 'Who can use SheOut',
      paragraphs: [
        `SheOut is for women aged 18 or over. To keep that promise, a person on our team checks a government ID and a live selfie before your first trip. Using someone else's ID, or false details, ends your access. ${TODO_LAWYER}`,
        'Before a partner can take trips, our team checks her ID, a live selfie, her driving licence and her vehicle papers.',
      ],
    },
    {
      heading: 'Where we operate',
      paragraphs: [
        'SheOut serves Hyderabad and the area around it. The pickup and the drop must both be inside that area; the app tells you if they are not.',
      ],
    },
    {
      heading: 'Your account',
      paragraphs: [
        'Your account is tied to your mobile number, and anyone who can read your SMS can sign in as you. Tell us at once if you lose your number.',
        'One number, one account. A customer account cannot also be a partner account.',
      ],
    },
    {
      heading: 'Fares and payment',
      paragraphs: [
        'The fare is shown before you book and is the fare you pay. It is worked out from the road route between your pickup and drop. It changes only if you change the drop during the trip.',
        'You pay in the app, from your SheOut wallet or online through Razorpay. Cash is not accepted. A trip left unpaid must be paid before you can book again.',
      ],
    },
    {
      heading: 'Cancelling and refunds',
      paragraphs: [
        'You can cancel before a trip starts. There is no cancellation fee today. If you often cancel after a partner has set off, we may limit your account.',
        'How refunds work is set out in the Refund and Cancellation Policy.',
      ],
    },
    {
      heading: 'Parcels',
      paragraphs: [
        'You must follow the Parcel Rules, including the list of items you must not send. The receiver must give the partner a delivery code. SheOut carries the parcel only; it is not part of any sale between you and someone else.',
      ],
    },
    {
      heading: 'Safety, and the SOS button',
      paragraphs: [
        'The SOS button alerts our safety team with your location. Today it also opens your own SMS app with a message to your emergency contacts, ready to send; SheOut will send those texts itself once its SMS service is approved.',
        'SOS is not an emergency service. In immediate danger, call 112 first.',
      ],
    },
    {
      heading: 'What we expect of you',
      paragraphs: [
        'Follow the Safety and Community Guidelines. Do not harass, threaten or endanger anyone, do not record anyone without their consent, do not contact another user outside a trip, and do not misuse or attack the service. We may suspend or close accounts that do, and report conduct that puts someone at risk.',
      ],
    },
    {
      heading: 'What we are responsible for',
      paragraphs: [
        'We run the platform with reasonable care, check who we admit, and act on safety reports.',
        `We cannot promise a partner will always be available, that a trip will take a particular time, or that the app will never be down. To the extent the law allows, we are not liable for losses beyond our reasonable control. Nothing here limits liability that the law does not allow to be limited. ${TODO_LAWYER}`,
      ],
    },
    {
      heading: 'Ending the agreement',
      paragraphs: [
        'You can stop using SheOut and delete your account at any time. We may suspend or close your account if these terms are broken, if your verification is withdrawn, or if the law requires it.',
      ],
    },
    {
      heading: 'Changes',
      paragraphs: ['If we change these terms in a way that matters, we will show you the new version in the app.'],
    },
    {
      heading: 'Law, disputes and complaints',
      paragraphs: [
        `These terms are governed by the laws of India, and the courts at ${E.courts} have jurisdiction. ${TODO_LAWYER}`,
        `Complaints: write to the Grievance Officer, ${consumerOfficer}. We acknowledge within ${E.grievanceAcknowledgeHours} hours and resolve within ${E.grievanceResolveDays} days.`,
      ],
    },
    whoWeAre(),
  ],
};

/** DRAFT, pending legal review. Partner classification and liability wording especially. */
export const PARTNER_TERMS: LegalDocument = {
  title: 'Partner Terms',
  effective: effective(),
  intro: `These terms are the agreement between you, as a SheOut partner, and ${E.legalName}, which runs SheOut. ${TODO_LAWYER}`,
  sections: [
    {
      heading: 'Your relationship with SheOut',
      paragraphs: [
        `You are an independent partner, not an employee of SheOut. You choose when to go online, and you may accept or decline any trip offered to you. SheOut provides the app that connects you with customers and handles payment. ${TODO_LAWYER}`,
      ],
    },
    {
      heading: 'Who can be a partner',
      paragraphs: [
        `You must be a woman aged 18 or over with a valid driving licence for your vehicle. Before your first trip our team checks your ID, a live selfie, your licence, and your vehicle's registration (RC), insurance and PUC certificate, and asks for a police verification certificate. Documents must be yours and current. ${TODO_LAWYER}`,
        'At the start of each shift you take a selfie, which is compared with your verified selfie before you can take trips.',
      ],
    },
    {
      heading: 'Your vehicle and documents',
      paragraphs: [
        'Keep your licence, RC, insurance and PUC valid. The app reminds you before a document expires and you cannot go online once one has. A vehicle in a family member\'s name is allowed if you tell us whose it is.',
      ],
    },
    {
      heading: 'Trips and conduct',
      paragraphs: [
        'Start a trip only with the customer\'s pickup code, and complete a parcel only with the receiver\'s delivery code. Photograph parcels at pickup and handover. Follow traffic law and the Safety and Community Guidelines. You may hold one trip at a time.',
      ],
    },
    {
      heading: 'Earnings and payouts',
      paragraphs: [
        'You keep the fare less SheOut\'s commission, which the app shows on each trip. Earnings from a parcel are held for 24 hours after delivery before they can be paid out. Payouts go to the bank account or UPI ID you add.',
        `${TODO_LAWYER}`,
      ],
    },
    {
      heading: 'Insurance',
      paragraphs: [
        'The app shows whether a trip is insured. A trip is insured only when the app says so. Your own vehicle insurance must be valid and, for rides, for commercial use.',
      ],
    },
    {
      heading: 'Suspension and ending',
      paragraphs: [
        'We may pause or end your access if your documents lapse, if you break these terms or the guidelines, if a safety report needs to be looked into, or if the law requires it. We will tell you why, unless the law or a safety investigation prevents it.',
        'You can stop being a partner at any time by deleting your account, once any trip in progress is finished and any payout due is paid.',
      ],
    },
    {
      heading: 'Your data',
      paragraphs: ['The Privacy Policy explains what we collect from partners and why.'],
    },
    {
      heading: 'Law, disputes and complaints',
      paragraphs: [
        `These terms are governed by the laws of India, and the courts at ${E.courts} have jurisdiction. ${TODO_LAWYER}`,
        `Complaints: write to the Grievance Officer, ${consumerOfficer}.`,
      ],
    },
    whoWeAre(),
  ],
};

/** DRAFT, pending legal review. Every rule here is one the code enforces today. */
export const REFUND_POLICY: LegalDocument = {
  title: 'Refund and Cancellation Policy',
  effective: effective(),
  intro: 'When you can cancel, when you are refunded, where the money goes and how long it takes.',
  sections: [
    {
      heading: 'Cancelling',
      paragraphs: [
        'You can cancel a ride or a parcel before it starts. There is no cancellation fee. If you often cancel after a partner has set off, we may limit your account.',
        'If SheOut or the partner cancels a trip you have already paid for, you are refunded in full.',
      ],
    },
    {
      heading: 'Where refunds go',
      paragraphs: [
        'Refunds go to your SheOut wallet straight away, and you can use the balance for any SheOut trip.',
        `In these cases the money goes back to the card, UPI or bank account you paid with instead, within ${E.refundToSourceWorkingDays} working days of the refund being approved:`,
      ],
      bullets: [
        'A payment failed but money left your account.',
        'You were charged twice for the same trip or top-up.',
        'SheOut cancelled a trip you had paid for online.',
      ],
    },
    {
      heading: 'Wallet balance',
      paragraphs: [
        'Your SheOut wallet can only be used to pay for SheOut trips. It cannot be sent to another person or withdrawn as cash.',
      ],
    },
    {
      heading: 'Unpaid trips',
      paragraphs: ['A completed trip must be paid. If a payment fails, you need to pay for that trip before you can book again.'],
    },
    {
      heading: 'Asking for a refund',
      paragraphs: [
        `Open the trip in the app and choose "Report an issue", or write to ${E.supportEmail}. If you are not happy with the answer, write to the Grievance Officer, ${consumerOfficer}.`,
      ],
    },
    {
      heading: 'Marketplace',
      paragraphs: [
        'Purchases from Marketplace sellers are between you and the seller. SheOut does not take payment for them and cannot refund them. A seller\'s monthly listing fee is not refunded if a shop is suspended; the paid month is kept for when it is reinstated.',
      ],
    },
    whoWeAre(),
  ],
};

/** DRAFT, pending legal review. Generated from the storage inventory in docs/legal-audit.md (section C). */
export const STORAGE_POLICY: LegalDocument = {
  title: 'Cookie and Local Storage Policy',
  effective: effective(),
  intro:
    'SheOut sets no cookies. The apps keep a few things in your browser\'s storage, and every one of them is needed for something you asked for. That is why there is no cookie banner: there is nothing optional to agree to.',
  sections: [
    {
      heading: 'What is stored in your browser',
      paragraphs: ['All of these are strictly necessary. None is used for advertising or tracking.'],
      bullets: [
        'Sign-in token and account ID. Keep you signed in, for up to 30 days.',
        'Language and light or dark theme. Until you change them.',
        'Your trip in progress. So the trip comes back if the page reloads.',
        'Emergency contacts, a waiting SOS, and your last known position. So SOS works with a weak signal.',
        'Notification token. Only after you allow notifications.',
        'Install ID and referral code. To stop referral fraud.',
        'Hints you have dismissed and your navigation voice setting.',
        'A note that the start screen was shown. Cleared when you close the tab.',
        'An offline copy of the app\'s pages, so it opens quickly and works with a weak signal.',
      ],
    },
    {
      heading: 'Things your browser fetches from others',
      paragraphs: [
        'The app loads its fonts from Google Fonts and, when you open a map, map images from Google Maps. Your browser sends your IP address to Google to fetch them. They set no cookies through SheOut.',
      ],
    },
    {
      heading: 'Clearing it',
      paragraphs: [
        'Signing out removes your sign-in token. Clearing your browser\'s site data removes everything; you will need to sign in again.',
      ],
    },
    {
      heading: 'If this changes',
      paragraphs: [
        'If we ever add anything optional, such as analytics, it will not load until you agree, refusing will be as easy as agreeing, and you will be able to change your mind in Settings.',
      ],
    },
  ],
};

/** Must match ParcelRules.PROHIBITED_ITEMS and its version (checked by scripts/check-legal-sync.mjs). */
export const PARCEL_PROHIBITED_ITEMS_VERSION = '2026-10-11';
export const PARCEL_PROHIBITED_ITEMS: Record<string, string> = {
  CASH: 'Cash',
  JEWELLERY: 'Jewellery',
  ALCOHOL: 'Alcohol',
  MEDICINES: 'Prescription or controlled medicines',
  WEAPONS: 'Weapons',
  FLAMMABLES: 'Flammables and chemicals',
  ILLEGAL: 'Anything illegal',
  LIVE_ANIMALS: 'Live animals',
  ORIGINAL_IDS: 'Original ID documents',
  CHEQUES_CARDS: 'Cheques and bank cards',
};

/** DRAFT, pending legal review. */
export const PARCEL_RULES: LegalDocument = {
  title: 'Parcel Rules',
  effective: effective(PARCEL_PROHIBITED_ITEMS_VERSION),
  intro: 'What you may send with SheOut, what you may not, and how a parcel is handed over.',
  sections: [
    {
      heading: 'Do not send',
      paragraphs: ['Before booking, you confirm your parcel contains none of these. The partner may refuse a parcel that seems to.'],
      bullets: Object.values(PARCEL_PROHIBITED_ITEMS),
    },
    {
      heading: 'Size and value',
      paragraphs: ['Up to 5 kg, and a value of up to ₹5,000. It must fit safely on a two-wheeler.'],
    },
    {
      heading: 'Handover',
      paragraphs: [
        'You name the receiver and give her the delivery code. The partner photographs the parcel at pickup and at handover, and completes the delivery only with that code. If she cannot hand it over, the app asks you to choose: she waits, she leaves it with someone you name, or she brings it back to you. Bringing it back adds the one-way fare again.',
        'Parcel photos and the receiver\'s number are deleted after 90 days unless there is an open dispute.',
      ],
    },
    {
      heading: 'Responsibility',
      paragraphs: [
        `SheOut carries the parcel only. It is not part of any sale between you and the receiver. Parcels are not insured. ${TODO_LAWYER}`,
      ],
    },
  ],
};

/** DRAFT, pending legal review. */
export const SAFETY_GUIDELINES: LegalDocument = {
  title: 'Safety and Community Guidelines',
  effective: effective(),
  intro: 'How everyone on SheOut is expected to behave, and what happens if they do not.',
  sections: [
    {
      heading: 'Everyone',
      bullets: [
        'Be respectful. No harassment, threats, insults or discrimination.',
        'Do not record anyone without their consent.',
        'Do not contact another person outside a trip, or use their number for anything else.',
        'No alcohol, drugs or weapons on a trip.',
        'Use your own account only.',
      ],
      paragraphs: [],
    },
    {
      heading: 'Customers',
      bullets: [
        'Give your pickup code only to the partner shown in the app, at the pickup.',
        'Be at the pickup on time, and cancel early if your plans change.',
      ],
      paragraphs: [],
    },
    {
      heading: 'Partners',
      bullets: [
        'Start only with the customer\'s pickup code, and follow the route.',
        'Follow traffic law.',
        'Never ask a customer for cash or personal details.',
      ],
      paragraphs: [],
    },
    {
      heading: 'If something goes wrong',
      paragraphs: [
        'In danger, call 112, then use SOS in the app. To report anything else, open the trip and choose "Report an issue". Our team looks at every safety report.',
        'Breaking these guidelines can lead to a warning, a pause or the end of your account, and we report crimes to the police.',
      ],
    },
  ],
};

/** DRAFT, pending legal review. */
export const GRIEVANCE_PAGE: LegalDocument = {
  title: 'Grievance Redressal',
  effective: effective(),
  intro: 'If you have a complaint about SheOut, a trip, a payment or how we handle your personal data, this is how to raise it.',
  sections: [
    {
      heading: 'Start in the app',
      paragraphs: [
        `Most problems are fastest through Help in the app, or by writing to ${E.supportEmail}.`,
      ],
    },
    {
      heading: 'Grievance Officer',
      paragraphs: [
        'If support has not resolved it, write to the Grievance Officer for your kind of complaint:',
        ...GRIEVANCE_REGIMES.map((regime) => {
          const r = E.grievanceOfficers[regime];
          return `For ${r.handles}: ${grievanceLine(regime)}, based in ${grievanceOfficer(regime).basedIn} (${r.rule}).`;
        }),
        'Please include your registered phone number and, for a trip, its date and time. Never send us your sign-in code.',
      ],
    },
    {
      heading: 'How long it takes',
      paragraphs: [
        `We acknowledge your complaint within ${E.grievanceAcknowledgeHours} hours and resolve it within ${E.grievanceResolveDays} days.`,
      ],
    },
    {
      heading: 'If you are not satisfied',
      paragraphs: [
        'For personal data, you can complain to the Data Protection Board of India. For a consumer complaint, you can approach the National Consumer Helpline (1915) or a consumer commission.',
      ],
    },
    whoWeAre(),
  ],
};

/** DRAFT, pending legal review. */
export const CONTACT_PAGE: LegalDocument = {
  title: 'Contact and business details',
  effective: effective(),
  intro: E.brandLine,
  sections: [
    whoWeAre(),
    {
      heading: 'Emergencies',
      paragraphs: ['SheOut is not an emergency service. In danger, call 112.'],
    },
  ],
};

/**
 * Agreeing to verification, shown to a partner before she uploads anything.
 * The server checks a partner agreed to exactly this version
 * (VERIFICATION_CONSENT_VERSION in its configuration), so the two are bumped
 * together - and bumping asks every partner to agree again. NOT APPROVED
 * WORDING: every paragraph carries the lawyer marker until it is.
 */
export const VERIFICATION_CONSENT_VERSION = '2026-10-03-draft';

export const PARTNER_VERIFICATION_CONSENT: LegalDocument = {
  title: 'Consent to verification',
  effective: `Version ${VERIFICATION_CONSENT_VERSION}`,
  intro: `I agree to SheOut verifying my identity, my documents and my police or criminal record, as described below, so that I can carry customers and deliveries on SheOut. ${TODO_LAWYER}`,
  sections: [
    {
      heading: 'What SheOut checks',
      paragraphs: [
        `My government ID and a live selfie, to confirm who I am. My driving licence, my vehicle\'s registration certificate (RC), its insurance, its PUC certificate and, for an auto or a cab, its fitness certificate, to confirm I may drive this vehicle for paying passengers. ${TODO_LAWYER}`,
        `A police verification certificate - from Telangana Police\'s portal, or another state\'s police - and, where SheOut arranges one, a background check report. My police verification is redone periodically, and I will be asked for a new certificate when it is due. ${TODO_LAWYER}`,
      ],
    },
    {
      heading: 'Who sees my documents',
      paragraphs: [
        `SheOut\'s verification team, through links that expire. Every time someone opens one of my documents, SheOut records who opened it and when. Customers never see my documents. ${TODO_LAWYER}`,
        `If SheOut uses a background-verification company, it receives what it needs to run the check, and SheOut will name it in the Privacy Policy before it is used. If I am enrolled in a group insurance cover, the insurer receives the details it needs to cover me and to handle a claim. ${TODO_LAWYER}`,
      ],
    },
    {
      heading: 'How long, and my choices',
      paragraphs: [
        `My documents are kept while I am a SheOut partner, and deleted with my account. Whether I was verified, and when, is kept as part of the record of the trips I took. ${TODO_LAWYER}`,
        `I can withdraw this consent by deleting my account. Without it, SheOut cannot verify me and I cannot go online. ${TODO_LAWYER}`,
      ],
    },
  ],
};

/**
 * Agreeing to a manual search of public court records, asked only when a
 * reviewer requests a screening (and only while ADMIN_COURT_SCREEN_ENABLED is
 * on). The server checks she agreed to exactly this version
 * (COURT_SCREEN_CONSENT_VERSION); CI keeps the two equal.
 * <p>
 * DRAFT, pending legal review: every paragraph carries the lawyer marker.
 * It must never describe the result as police or background verification.
 */
export const COURT_SCREEN_CONSENT_VERSION = '2026-10-14-draft';

export const COURT_SCREEN_CONSENT: LegalDocument = {
  title: 'Consent to a court-record check',
  effective: `Version ${COURT_SCREEN_CONSENT_VERSION}`,
  intro: `I agree to SheOut searching public court records for my name, as described below, as part of deciding whether I can carry customers. ${TODO_LAWYER}`,
  sections: [
    {
      heading: 'What SheOut does',
      paragraphs: [
        `A trained member of SheOut's team searches the public eCourts website by hand for cases under my name and its spellings, in Hyderabad, Rangareddy, Medchal-Malkajgiri and my home district. Nothing is searched automatically. This is a screening, not police verification. ${TODO_LAWYER}`,
        `If a record might be mine, a second person checks it. Before any decision against me, SheOut tells me and gives me time - the app shows the date - to explain or to send my own police clearance certificate. ${TODO_LAWYER}`,
      ],
    },
    {
      heading: 'What is kept',
      paragraphs: [
        `Each search: where, under which name, when, and what was found. For a possible match, only the type of case, its status, the year and the court - never the judgment or anyone else's name. ${TODO_LAWYER}`,
        `If I am not cleared, the reason category is kept and the details are anonymised after 30 days. When I leave SheOut, the record is anonymised after 12 months. ${TODO_LAWYER}`,
      ],
    },
    {
      heading: 'My choices',
      paragraphs: [
        `I can withdraw this consent at any time in the app. The search then stops. Without it, SheOut may not be able to let me carry customers. ${TODO_LAWYER}`,
      ],
    },
  ],
};

/** Every public legal page, by the path it lives at in both apps. */
export const LEGAL_PAGES = {
  '/privacy': PRIVACY_POLICY,
  '/terms': TERMS_OF_SERVICE,
  '/partner-terms': PARTNER_TERMS,
  '/refunds': REFUND_POLICY,
  '/storage-policy': STORAGE_POLICY,
  '/parcel-rules': PARCEL_RULES,
  '/safety-guidelines': SAFETY_GUIDELINES,
  '/grievance': GRIEVANCE_PAGE,
  '/contact': CONTACT_PAGE,
} as const satisfies Record<string, LegalDocument>;

export type LegalPath = keyof typeof LEGAL_PAGES;
