/*
 * Every word and link on the site, in one place. Claims here mirror what the
 * apps themselves tell people (About, onboarding, the safety screens) - the
 * site promises nothing the product does not do. Keep it that way: no
 * invented ratings, rider counts or reviews.
 */
export const LINKS = {
  riderApp: 'https://app.sheoutride.com',
  partnerApp: 'https://partner.sheoutride.com',
  sellers: 'https://app.sheoutride.com/seller',
  terms: 'https://app.sheoutride.com/terms',
  privacy: 'https://app.sheoutride.com/privacy',
  support: 'mailto:support@sheoutride.com',
  supportEmail: 'support@sheoutride.com',
};

export const NAV = [
  { href: '#how', label: 'How it works' },
  { href: '#safety', label: 'Safety' },
  { href: '#partners', label: 'Drive with us' },
  { href: '#sellers', label: 'Sell' },
  { href: '#faq', label: 'FAQ' },
];

export const HERO = {
  eyebrow: 'Now riding in Hyderabad',
  lines: ['Rides by women,', 'for women.'],
  body: 'Book a bike taxi or send a parcel across the city. Every partner is a verified woman, the fare is fixed before you book, and SOS is one tap away on every trip.',
  primary: 'Book a ride',
  secondary: 'Drive with SheOut',
  chips: ['ID and police-verified partners', 'Fixed fare before you book', 'SOS on every trip'],
};

export const TICKER = [
  'Women riders',
  'Women partners',
  'Bike taxi',
  'Parcel delivery',
  'Made-by-women marketplace',
  'English · हिन्दी · తెలుగు',
  'Wallet, UPI and cards',
  'Never cash',
];

export const SERVICES = [
  {
    key: 'bike',
    title: 'Bike Taxi',
    tag: 'Ride with a woman partner',
    body: 'A verified woman picks you up and brings a spare helmet for you. See the route and the fare before you book.',
    tint: 'from-primary-light to-white',
  },
  {
    key: 'parcel',
    title: 'Parcel Delivery',
    tag: 'Send it across the city',
    body: 'Documents, lunch, a gift. A woman partner collects it and you follow it on the map until it is delivered.',
    tint: 'from-accent-orange-tint to-white',
  },
  {
    key: 'market',
    title: 'Marketplace',
    tag: 'Made by women, near you',
    body: 'Sarees, jewellery, mehandi, tailoring and beauty, straight from the women in Hyderabad who make them.',
    tint: 'from-[#FCE7F3] to-white',
  },
] as const;

export const STEPS = [
  {
    title: 'Tell us where you are going',
    body: 'Pick your drop. You see the route, how long it takes and a fixed fare - before you book.',
    screen: 'rider-book-ride',
    alt: 'Booking screen with the route from Banjara Hills to Hitech City and a fixed fare of 107 rupees',
  },
  {
    title: 'A verified woman partner accepts',
    body: 'Her name, photo, rating and number plate appear, and you watch her come to you on the live map.',
    screen: 'rider-partner-on-the-way',
    alt: 'Live map with the partner on her way, arriving in about 2 minutes, with her name and number plate',
  },
  {
    title: 'You check each other',
    body: 'She confirms you are the rider in your photo. You confirm she is the partner shown. Only then do you share your 4-digit pickup code.',
    screen: 'rider-partner-arrived',
    alt: 'Partner has arrived screen asking the rider to check the face, number plate and helmet before getting on',
  },
  {
    title: 'Ride with someone watching over you',
    body: 'Share your live trip with family in one tap. SOS stays on screen for the whole ride.',
    screen: 'rider-on-trip',
    alt: 'On-trip screen with the route to the drop, Share trip and an SOS button',
  },
  {
    title: 'Pay in the app, never in cash',
    body: 'Pay from your SheOut wallet, by UPI or by card when you arrive.',
    screen: 'rider-trip-complete',
    alt: 'Payment screen offering the SheOut wallet, UPI apps or a card',
  },
];

export const SAFETY = {
  eyebrow: 'Safety is the product',
  title: 'Not a setting you have to find. It is how every trip works.',
  cards: [
    {
      key: 'checked',
      title: 'Every partner is checked',
      body: 'ID with a live selfie, police verification, and her licence, RC and insurance reviewed by a person before her first trip.',
    },
    { key: 'riders', title: 'Riders are verified too', body: 'Every rider confirms who she is with a government ID before her first ride, so partners know who they are picking up.' },
    { key: 'code', title: 'A code only you can give', body: 'The trip starts only when you tell your partner your 4-digit pickup code.' },
    { key: 'share', title: 'Live trip sharing', body: 'Family follows your ride on the map, in real time, from one link.' },
    {
      key: 'sos',
      title: 'SOS on every trip',
      body: "One tap alerts SheOut's safety team with your location and messages your emergency contacts. Call 112 from the same screen.",
    },
  ],
};

export const STATS = [
  { value: 100, suffix: '%', label: 'women partners' },
  { value: 3, suffix: '', label: 'languages: English, हिन्दी, తెలుగు' },
  { value: 1, suffix: ' tap', label: 'to raise an SOS' },
  { value: 0, suffix: '', label: 'fares paid in cash' },
];

export const PARTNERS = {
  eyebrow: 'For women who drive',
  title: 'Earn on your own hours.',
  body: 'Ride with women only, choose when you work, and get paid straight to your bank account or UPI.',
  points: [
    'Go online when it suits you - and offline whenever you like.',
    'See the pickup, drop and fare before you accept a ride.',
    'Every rider is a verified woman.',
    'Request a payout any time; it usually reaches you within two working days.',
  ],
  needs: 'You will need a government ID, a driving licence, your vehicle RC and insurance, and to pass police verification.',
  cta: 'Join as a partner',
};

export const SELLERS = {
  eyebrow: 'For women who make things',
  title: 'Open your shop on SheOut.',
  body: 'List your sarees, jewellery, mehandi, tailoring or beauty services where SheOut riders already are. Buyers send you an enquiry, and a SheOut partner can carry the parcel.',
  price: '₹499 a month',
  priceNote: 'Your first month is free. Nothing is charged automatically.',
  cta: 'Start selling',
};

export const FAQ = [
  {
    q: 'Who can use SheOut?',
    a: 'SheOut is for women - riders and partners both. Everyone confirms who they are with a government ID, reviewed by a person, before their first trip.',
  },
  { q: 'Where does SheOut work?', a: 'In Hyderabad, for now.' },
  {
    q: 'How is my fare worked out?',
    a: 'From the route and the time it takes. You see it as a fixed price before you book, and that is what you pay - unless you change your destination during the ride.',
  },
  { q: 'How do I pay?', a: 'In the app: from your SheOut wallet, by UPI or by card. Fares are never paid in cash.' },
  {
    q: 'What happens when I press SOS?',
    a: "SheOut's safety team is alerted with your live location, and the emergency contacts you saved are messaged. You can also call 112 from the same screen.",
  },
  {
    q: 'Do I need to download an app?',
    a: 'No store download is needed. Open app.sheoutride.com on your phone and add it to your home screen - it works just like an app.',
  },
  { q: 'Which languages can I use?', a: 'English, हिन्दी and తెలుగు. You can switch at any time in Settings.' },
  {
    q: 'How do I become a partner?',
    a: 'Sign up at partner.sheoutride.com and upload your documents. Our team reviews them and the police verification, and you can start taking rides once you are approved.',
  },
];
