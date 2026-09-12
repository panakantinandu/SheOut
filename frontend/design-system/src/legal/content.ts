/**
 * The privacy policy and terms both apps show.
 * <p>
 * Written against what this system actually does, not from a template. Every
 * claim below was checked against the code: the fields the API really
 * stores, the third parties requests really reach, who can really see an
 * identity document, and how long a session really lasts. A policy that
 * describes a different product is worse than none, because it is a promise
 * nobody kept.
 * <p>
 * NOT LEGAL ADVICE, AND NOT FINISHED. Everything marked {@link TODO_LEGAL}
 * is a fact only the company can supply - the registered entity, its
 * address, the grievance officer required of an Indian intermediary, the
 * retention periods the business is willing to commit to. They are left
 * visibly blank on purpose. Inventing a registered address or naming a
 * grievance officer who does not exist would make this document a
 * misrepresentation rather than an incomplete draft, and it would be
 * relied on by real users.
 * <p>
 * India's Digital Personal Data Protection Act 2023 is the regime this is
 * shaped for: notice at collection, purpose limitation, a named grievance
 * contact, and consent for each distinct purpose. The gender-verification
 * document and precise location are both sensitive enough to deserve
 * naming individually rather than hiding under "we collect usage data",
 * which is what most policies do with them.
 */

/** A placeholder that must be filled before launch. Rendered visibly. */
export const TODO_LEGAL = '[to be completed by SheOut]';

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

/** Bumped whenever the substance changes, not the wording. */
export const LEGAL_VERSION = '2026-09-12';

export const PRIVACY_POLICY: LegalDocument = {
  title: 'Privacy Policy',
  effective: `Version ${LEGAL_VERSION}`,
  intro:
    'SheOut is a women-only ride and delivery service. Running it safely means we ask for more about you than a taxi app normally would, including a government ID and your live location. This page says exactly what we take, why each thing is needed, who sees it, and what you can ask us to do about it. Nothing here is written to be skimmed past.',
  sections: [
    {
      heading: 'What we collect, and why each item',
      paragraphs: [
        'We do not collect anything on this list "just in case". Each item exists because a specific part of the service stops working without it.',
      ],
      bullets: [
        'Your mobile number. It is your login - we have no passwords at all, only a one-time code sent by SMS. It is also how a driver and a rider are reached about a trip in progress.',
        'Your name. Shown to the person you are travelling with or handing a parcel to, so each of you knows who to expect.',
        'A government identity document. This is the sensitive one. SheOut is women-only, and that promise is worth nothing if it is self-declared, so a person on our operations team looks at your ID and decides. See "Who can see your ID" below.',
        'Your precise location. A rider\'s location sets the pickup point. A driver\'s location is broadcast while they are online, so that riders nearby can be matched and so a rider can watch their driver approach. A driver who goes offline stops broadcasting immediately.',
        'The addresses you search for. Typing a destination sends that text, and dropping a pin sends those coordinates, to OpenStreetMap\'s geocoding service. See "Who else receives your data".',
        'Your emergency contacts. Names, numbers and relationships you enter yourself. These are only used when you raise an SOS. Please read "About your emergency contacts" - you are giving us someone else\'s phone number.',
        'Your trips and payments. Pickup, drop, time, fare, and how it was paid. Needed to run the trip, settle with drivers, answer disputes and meet tax obligations.',
        'For drivers only: vehicle type and registration number, your online and offline periods, and the outcome of a police verification check.',
      ],
    },
    {
      heading: 'What we deliberately do not collect',
      paragraphs: [
        'Stated plainly so you can hold us to it.',
      ],
      bullets: [
        'No passwords, because there is no password to steal.',
        'No card numbers or UPI IDs. Trips are paid per ride, and nothing about a payment instrument is stored on your account.',
        'No contact list, photo library, microphone or camera roll access. We never ask for them.',
        'No location at all while the app is closed. A driver broadcasts only while online in the app; a rider\'s location is read only when they ask for it to set a pickup.',
        'No advertising identifiers, and no sale of anything on this page to anybody. We do not run ads and we do not have data-broker relationships.',
      ],
    },
    {
      heading: 'Who can see your ID',
      paragraphs: [
        'Your identity document is visible to trained members of the SheOut operations team who review verification submissions, and to nobody else. It is never shown to drivers, to riders, or to anyone you travel with. It is not published, indexed or shared.',
        'Verification is a human decision. No automated system approves or rejects you, and none is planned, because getting this wrong excludes a woman from a service built for her.',
        'A rejected document can be replaced and reviewed again. We do not keep a rejected submission any longer than the review needs.',
      ],
    },
    {
      heading: 'About your emergency contacts',
      paragraphs: [
        'When you add an emergency contact you are giving us the phone number of someone who has not agreed to anything with us. We treat that seriously.',
        'We use it for one purpose only: if you raise an SOS, we send that person an SMS telling them you raised an alert and where you were. We never message them at any other time, for any reason, and they are never added to any list.',
        'Please tell the people you add that you have done so. If a contact asks us to remove their number, we will, and we will tell you that the contact was removed so you are not left believing someone will be told when they will not be.',
      ],
    },
    {
      heading: 'Who else receives your data',
      paragraphs: [
        'These are the only third parties involved, and what each one gets. Each is a processor acting on our instructions, not a party we sell to.',
      ],
      bullets: [
        'Twilio, for SMS. Receives the destination phone number and the message text - a login code, a trip update, or an SOS alert.',
        'OpenStreetMap\'s Nominatim geocoder, for turning what you type into a place and a pin into an address. Receives the search text or the coordinates. It does not receive your name, number or account.',
        'Google, only if you choose Sign in with Google. Receives nothing from us; we receive your email address and name from them.',
        'Razorpay, for online payments, when a trip is paid that way. Receives the payment amount and reference, and handles the instrument itself so we never hold it.',
        'Our hosting providers, which store the database and the uploaded documents in order to run the service at all.',
      ],
    },
    {
      heading: 'How long we keep things',
      paragraphs: [
        `Retention periods are a commitment the business has to make and has not made yet: ${TODO_LEGAL}. What we can tell you today is the shape of it.`,
        'One-time codes live for minutes, in memory, and are destroyed the moment they are used or guessed at too many times. A signed-in session lasts up to 30 days before you must sign in again. Trip and payment records have to be kept for as long as tax and dispute-resolution rules require. Identity documents are kept while your account is verified, because a verification we cannot evidence is a verification we cannot stand behind.',
      ],
    },
    {
      heading: 'Keeping it safe, and what we cannot promise',
      paragraphs: [
        'Traffic between the apps and our servers is encrypted in transit. Access to the operations console is restricted to a small number of named accounts, each signing in with a one-time code, and no ordinary user account can reach it.',
        'We will not claim to be unbreachable. If personal data of yours is exposed, we will tell you what happened, what was involved, and what we did, rather than waiting to be asked.',
      ],
    },
    {
      heading: 'What you can ask us to do',
      paragraphs: [
        'You can ask for a copy of what we hold about you, ask us to correct it, ask us to delete your account, or withdraw a consent you gave. Two honest caveats. Deleting your account does not delete a completed trip\'s financial record, which we are required to retain. And withdrawing consent for the identity check means we can no longer offer you rides, because the women-only promise is the product.',
        `To make a request, or to complain about how we handled your data, contact the grievance officer: ${TODO_LEGAL}. Under Indian law you are entitled to a named person and a response within a defined period, and both belong here before launch.`,
      ],
    },
    {
      heading: 'Children',
      paragraphs: [
        'SheOut is not for anyone under 18. We do not knowingly collect anything from a child, and we will delete an account we learn belongs to one.',
      ],
    },
    {
      heading: 'Changes to this policy',
      paragraphs: [
        'If we change what we collect or why, we will show you the new version and ask again rather than quietly updating this page. The version number at the top is how you can tell which one you agreed to.',
      ],
    },
    {
      heading: 'Who we are',
      paragraphs: [
        `The company responsible for your data, its registered address, and its registration details: ${TODO_LEGAL}.`,
      ],
    },
  ],
};

export const TERMS_OF_SERVICE: LegalDocument = {
  title: 'Terms of Service',
  effective: `Version ${LEGAL_VERSION}`,
  intro:
    'These terms are the agreement between you and SheOut. They are written to be read, not to be survived. The parts that matter most are what we are actually responsible for, and what happens when something goes wrong on a trip.',
  sections: [
    {
      heading: 'What SheOut is, and what it is not',
      paragraphs: [
        'SheOut connects riders with independent driver-partners for rides and deliveries. We are the platform that matches you, prices the trip and handles payment.',
        'We are not the driver, and driver-partners are not our employees. That is a real limit on what we can promise about any individual trip, and it would be dishonest to imply otherwise. What we do take responsibility for is who we let onto the platform, and what we do when you tell us something went wrong.',
      ],
    },
    {
      heading: 'Women only, and why we check',
      paragraphs: [
        'Both riders and driver-partners on SheOut are women. This is the point of the service, not a marketing line.',
        'It is why we ask for a government ID and why a person reviews it. Submitting someone else\'s document, or a document that is not yours, ends your access permanently - not because of paperwork, but because it puts other women in a car with someone who lied to get there.',
        'Driver-partners are additionally subject to a police verification check and cannot accept trips until both checks pass. We re-check both every time a trip is offered, so a partner whose verification is withdrawn stops receiving trips immediately rather than at some later sweep.',
      ],
    },
    {
      heading: 'Where we operate',
      paragraphs: [
        'SheOut currently serves Hyderabad and the area around it. Both the pickup and the drop of a trip must fall within that area. If you choose a location outside it, the app will tell you at the time rather than accepting the trip and failing later.',
      ],
    },
    {
      heading: 'Your account',
      paragraphs: [
        'Your account is yours alone and is tied to your mobile number. Anyone holding your phone and able to read an SMS can sign in as you, so treat your number as the credential it is and tell us promptly if you lose control of it.',
        'One number, one account, one role. A number registered as a rider cannot also be a driver-partner.',
      ],
    },
    {
      heading: 'Fares and payment',
      paragraphs: [
        'The fare shown before you book is calculated by the same method the booking itself uses, so it is the price of the trip and not a teaser. It is based on the straight-line distance between your two points, which we tell you because a road route is usually longer and a policy of quiet surprises is not one we want.',
        'A completed trip is payable. Where a final fare differs from the estimate, the reason will be visible on the trip.',
      ],
    },
    {
      heading: 'Cancelling',
      paragraphs: [
        'You can cancel before a trip starts. Repeatedly cancelling after a partner has already set off wastes the time of a woman who is working, and we may restrict an account that does it persistently.',
      ],
    },
    {
      heading: 'Safety, and the SOS button',
      paragraphs: [
        'The SOS button alerts your emergency contacts by SMS with your location, and raises the alert to our operations team.',
        'Two things you must understand about it. SOS is not an emergency service - in immediate danger, call the police on 100 or the national emergency number 112 first. And an SMS can fail: if we could not reach a contact, the app tells you so instead of showing a reassuring tick. If it says nobody was reached, nobody was reached.',
      ],
    },
    {
      heading: 'What we expect of you',
      paragraphs: [
        'Do not use SheOut to harass, threaten, record or endanger anyone. Do not carry anything illegal, dangerous or prohibited as a parcel. Do not attempt to identify, follow or contact another user outside a trip. Do not interfere with, probe or attempt to break the service.',
        'We will suspend or end access for any of these, and we will report conduct that endangers someone.',
      ],
    },
    {
      heading: 'What we are responsible for',
      paragraphs: [
        'We are responsible for operating the platform with reasonable care, for verifying who we admit, and for acting on safety reports.',
        'We cannot guarantee a partner will always be available, that a trip will take a particular time, or that the app will never be unavailable. To the extent the law allows, we are not liable for losses beyond our reasonable control - and nothing here limits liability that cannot lawfully be limited, including for death or personal injury caused by our own negligence, or for fraud.',
      ],
    },
    {
      heading: 'Ending the agreement',
      paragraphs: [
        'You can stop using SheOut and ask us to delete your account at any time. We may end or suspend access if these terms are broken, if a verification is withdrawn, or if we are required to.',
      ],
    },
    {
      heading: 'Changes',
      paragraphs: [
        'If we change these terms materially we will show you the new version and ask you to accept it. Continuing to use the service after that is how you accept it.',
      ],
    },
    {
      heading: 'Law, disputes, and who to contact',
      paragraphs: [
        `These terms are governed by Indian law. The courts with jurisdiction, the company\'s registered details, and the grievance officer required of an intermediary: ${TODO_LEGAL}.`,
      ],
    },
  ],
};
