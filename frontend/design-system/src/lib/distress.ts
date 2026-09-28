/**
 * Does a message to SheOut Help sound like someone in danger right now?
 * <p>
 * The same patterns as the server's EmergencyDetector (backend assistant
 * module) - change both together. Checked in the app before anything is
 * sent, so the SOS panel appears instantly, even offline; the server checks
 * again and never passes such a message to the model. Tuned for recall:
 * "help" on its own matches, "help with my payment" does not.
 */
const PATTERNS: RegExp[] = [
  /^\W*(please\W+)?(help|sos|emergency)\W*(me|us|now|asap|urgent(ly)?|quick(ly)?|fast|please|pls|plz)?\W*$/,
  /\b(help|save)\s+(me|us)\b/,
  /\b(somebody|someone|anyone)\s+help\b/,
  /\bneed\s+help\s+(now|right\s+now|urgently|fast|quickly|immediately)\b/,
  /\b(i'?m|i\s+am|feel(ing)?|so|very|really)\s+(scared|afraid|frightened|terrified|unsafe|in\s+danger)\b/,
  /\b(scared|terrified|frightened)\b/,
  /\b(following|followed|follows|stalking|stalked|chasing|chased)\s+(me|us)\b/,
  /\bbeing\s+(followed|stalked|chased|watched)\b/,
  /\b(touch(ed|ing)?|grab(bed|bing)?|hit(ting)?|hurt(ing)?|attack(ed|ing)?|threaten(ed|ing)?)\s+me\b/,
  /\b(harass(ed|ing|ment)?|molest(ed|ing)?|assault(ed)?|rape(d)?|kidnap(ped|ping)?|abduct(ed|ing)?)\b/,
  /\b(won'?t|will\s+not|not)\s+let\s+me\s+(out|go|leave|get\s+out)\b/,
  /\blocked\s+(me\s+in|the\s+doors?|in)\b/,
  /\b(wrong|different|strange)\s+(way|road|route|direction)\b.*\b(scared|afraid|won'?t\s+stop|not\s+stopping)\b/,
  /\b(not|won'?t)\s+stop(ping)?\s+the\s+(car|auto|bike|vehicle)\b/,
  /\b(knife|gun|weapon)\b/,
  /\b(in\s+danger|unsafe\s+right\s+now|call\s+(the\s+)?police|trapped)\b/,
  /\b(bachao|bachaao|bacha\s+lo|madad\s+karo|madat\s+karo|dar\s+lag|darr\s+lag|peecha|pichha|picha)\b/,
  /\b(khatra|khatre\s+mein|chhed|ched\s+raha)\b/,
  /\b(kapadandi|kaapadandi|kapadu|bhayam|bayam\s+ga|vembadi)\b/,
  /(बचाओ|बचा लो|मदद करो|मदद कीजिए|डर लग|पीछा|छेड़|ख़तरा|खतरा|ख़तरे|खतरे)/,
  /(కాపాడండి|కాపాడు|రక్షించండి|సహాయం చేయండి|భయం|భయంగా|వెంబడి|ప్రమాదం)/,
];

export function soundsLikeEmergency(message: string): boolean {
  if (!message || !message.trim()) return false;
  const text = message.normalize('NFKC').toLowerCase().replace(/’/g, "'").replace(/\s+/g, ' ').trim();
  return PATTERNS.some((p) => p.test(text));
}
