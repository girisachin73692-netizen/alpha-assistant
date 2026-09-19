# Alpha Voice Assistant (Android)

## Isme kya kya hai

1. **Wake word sirf "Alpha"** — sirf "Alpha" ya "अल्फा" bolne par hi activate hoga.
   Kuch aur bola to (jaise "Hello Alpha" ya koi bhi background baat), wo IGNORE ho jaayega.
2. **Reply**: Wake word sunte hi app bolega — **"हाँ जी बॉस"**
3. Uske baad ek command sun-ta hai (abhi "time"/"समय" command demo ke taur par diya hai —
   aap `MainActivity.kt` ke `handleCommand()` function me aur commands add kar sakte ho).
4. **Female voice (ElevenLabs)** ke liye support already code me daala hua hai
   (`ElevenLabsHelper.kt`) — bas apni API key daalni hai.

## Project kaise open karein

1. **Android Studio** install karo (agar nahi hai): https://developer.android.com/studio
2. Android Studio kholo → **Open** → is `AlphaAssistant` folder ko select karo
3. Gradle sync hone do (pehli baar thoda time lagega, internet chahiye)
4. Phone ko USB se connect karo (USB debugging ON karke) ya emulator use karo
5. Upar "Run" (green play button) dabao

## Aapka apna Alpha AI server jodne ke liye (real AI reply)

Aapke Termux wale Alpha server (`main.py`, FastAPI) se app ko jod diya gaya hai.
Chat screen ab pehle bundled voice replies check karega, phir aapke **apne AI
server** ko call karega — agar wo bhi match na ho to tabhi purani local reply
dikhegi.

### Enable karne ke liye

1. Phone par Termux mein apna Alpha server chalu rakho (`main.py`, port 8000)
2. File kholo: `app/src/main/java/com/alpha/assistant/AlphaServerClient.kt`
3. Ye line edit karo:
   ```kotlin
   private const val ALPHA_API_KEY = "yaha apni .env wali ALPHA_API_KEY paste karo"
   ```
4. Save karo, app run karo — ab Chat screen mein jo bhi type karoge, wo
   aapke apne AI (OpenRouter ke through) se real jawab dega.

**Note:** Server aur app dono isi phone par hone chahiye (Termux background
mein chalta rehna chahiye jab tak app use kar rahe ho). Agar server band ho
ya key galat ho, app automatically bata dega "server se jawab nahi mila".

**Voice assistant bhi ab isi AI server se connect hai** — jab "Alpha" bolo,
koi bundled voice reply match na ho to wo bhi background service se aapke AI
server ko call karega aur uska jawab bolega (phone TTS/ElevenLabs se).
Same `ALPHA_API_KEY` (upar wali file mein) dono - Chat aur Voice - use karte hain.

## Alpha ab thoda "smart" hai (bina API key ke)

`AlphaBrain.kt` mein ab:
- Punctuation/extra spaces ignore hote hain (jaise "समय?" bhi match hoga)
- Naye commands add hue: **तारीख/date**, **कौनसा दिन है**, **तुम्हारा नाम क्या है**,
  **कैसी हो**, greeting fallback ("नमस्ते"/"hello" par bhi reply)
- Har command ke multiple tareeke (synonyms) samajhta hai — jaise "समय", "टाइम",
  "kitna baja" sab time ke liye kaam karenge

Ye abhi bhi rule-based hai, real AI nahi — jab bhi real AI (agent) chahiye ho
(jisse Alpha KUCH BHI samajh sake, sirf fixed commands tak limited na ho),
bata dena — uske liye ek AI API key (Claude/OpenAI) chahiye hogi.

## Naya Chat Screen (text se baat karne ke liye)

Ab app mein ek **Chat screen** bhi hai (is Claude jaise) — MainActivity par
"Chat kholiye" button dabao. Isme:

- Type karke Alpha se baat kar sakte ho (voice ke alawa)
- Upar-right mein **3-dot menu** hai: Clear chat / Voice mode par jao / Alpha ke baare mein
- Jawab wahi logic se aata hai jo voice assistant use karta hai (`AlphaBrain.kt`
  - single shared file) — matlab chat aur voice dono consistent rahenge

**MainActivity (voice wala screen) bilkul waisa hi hai jaisa pehle tha** — sirf
ek chhota button add hua hai chat kholne ke liye, baaki kuch nahi badla.

**Note**: Ye abhi bhi rule-based hai (real AI agent nahi) — jaisa aapne bola
tha, agent baad mein add karenge. Abhi ke liye ye wahi fixed commands/audio
replies match karta hai, chat format mein.

## Aapki asli Kanika/ElevenLabs voice — ab HAR fixed command mein use ho sakti hai

App mein ab ek simple system hai (`voiceReplies` map, `AlphaListenerService.kt` ke andar)
jisme aap jitne bhi **fixed** replies chahte ho (jinka jawab hamesha same rehta hai —
jaise "नमस्ते", "धन्यवाद", "ठीक है बॉस" वगैरह), unhe isi asli recorded voice mein
bulwa sakte ho — **bina kisi API key ke**.

### Naya voice reply add karne ka process (bahut simple hai)

1. ElevenLabs mein (Kanika voice se) jo bhi phrase chahiye, type karke generate karo
2. Us audio ko download karke mujhe bhej do (jaise pehle "हाँ जी बॉस" bheja tha)
3. Main us file ko `res/raw/` mein daal dunga aur `voiceReplies` map mein
   sirf **ek line** add kar dunga — bas itna hi kaam hai, dobara se poora
   code nahi likhna padta

Jitni marzi phrases bhejo, sab isi tarah add ho jaayengi.

### Kya cheez isse NAHI ho sakti (aur kyun)

Sirf wo cheezein jo **har baar badalti hain** (jaise current time — 5:30, 5:45,
kabhi bhi kuch bhi ho sakta hai) — unko pehle se record nahi kiya ja sakta,
kyunki recording sirf wahi bol sakti hai jo usme record hai. Aisi dynamic
cheezon ke liye ya to:
- Phone ki normal built-in awaaz chalegi (abhi automatic fallback yehi hai), ya
- ElevenLabs API key add karo (real-time generate karega, thoda sa cost/free-credits use hoga)

## Female voice — ab bina kisi setup ke bhi milegi

App ab automatically phone ke built-in TTS engine se **female voice** dhundh
kar use karta hai (`selectFemaleVoice()` function, `AlphaListenerService.kt` mein) —
isme kuch bhi extra karne ki zaroorat nahi, turant kaam karega.

Agar phone mein **Google Text-to-Speech** app installed/updated hai (zyada tar
Android phones mein already hoti hai), to ye best result dega. Check karne ke liye:
Settings → System → Languages & input → Text-to-speech output → Google TTS ko
default engine banao, aur Hindi (hi-IN) voice data download kar lo agar pehli
baar use kar rahe ho.

**Zyada natural/better quality female voice** chahiye to ElevenLabs wala option
niche follow karo — wo asli AI voice deta hai, phone ke robotic TTS se kaafi behtar.

## Female voice (ElevenLabs) enable karne ke liye

1. https://elevenlabs.io par account banao, API key copy karo
2. Apni pasand ki female voice choose karo, uska **Voice ID** copy karo
3. File kholo: `app/src/main/java/com/alpha/assistant/ElevenLabsHelper.kt`
4. Ye 2 lines edit karo:
   ```kotlin
   private const val API_KEY = "yaha apni API key paste karo"
   private const val VOICE_ID = "yaha apni voice ID paste karo"
   ```
5. Save karo, app phir se run karo.

**Note:** Jab tak API key nahi daalte, app automatically Android ke built-in
voice (jo bhi female voice phone me available hai) se bolega — taki app turant
kaam kare, ElevenLabs baad me add kar sako.

## Time galat batane wala problem

Time ab device ke system clock se directly liya jaata hai
(`SimpleDateFormat("hh:mm a")`), isliye galat time ki dikkat nahi aani chahiye.
Agar phir bhi galat aaye, phone ki date/time settings check karo (auto time zone ON hona chahiye).

## "Key dabane par kuch na hona" wala bug

Purane app me jo bhi physical/UI key logic tha wo yaha shuru se nahi hai —
ye naya app sirf **voice se** control hota hai, isliye wo purana bug yaha
apply hi nahi hota. Agar aapko koi physical button (jaise volume key) se
bhi Alpha ko manually trigger karna hai, bata dena — wo alag se add kar dunga.

## Background me hamesha chalna (ADD HO CHUKA HAI)

Ab Alpha ek **Foreground Service** (`AlphaListenerService.kt`) me chalta hai:

- App open karte hi service start ho jaata hai aur ek permanent notification dikhata hai
  ("Alpha - सुन रहा हूँ...") — isse Android usko background me band nahi karega.
- App minimize/band karne par bhi Alpha sunta rehta hai.
- Phone restart hone par bhi (`BootReceiver.kt`) service khud start ho jaata hai.
- MainActivity ab sirf ek status screen hai — asli sunne/bolne ka kaam
  service ke andar hota hai.

Service ko band karna ho to: notification par tap karke app kholo, phir
Android Settings > Apps > Alpha > Force Stop (ya notification ko swipe karke
band karne ka button add karwa sakte ho agar chahiye).

## Aur commands add karne ke liye

`AlphaListenerService.kt` ke `handleCommand()` function me naye `when` cases add karo.
