"""
Reproducible corpus generator: extends the PII test corpus to 500 samples
(target 300 negative food-data patterns + 200 positives).

Design constraints (must hold so the corpus stays valid for BOTH readers):
  * No commas in any field (the Java corpus test splits naively on commas).
  * Negatives never start with '+' or with exactly '00x' (x != 0), so the
    detector's international-normalization can never turn them into a phone;
    numeric barcodes start 1-9; leading-zero identifiers always start '000'
    (caught by the leading-zeros exclusion rule before normalization).
  * In-scope phone positives are FORMAT variants (MR2) of numbers already
    confirmed detected in the current corpus -> guaranteed valid EU numbers.

Run once with any Python 3 (stdlib only). Idempotent: does nothing if the
corpus already has >= 500 rows.
"""
import csv
import os
import random

random.seed(20260827)

CORPUS = os.path.join(
    os.path.dirname(__file__),
    "..", "FoodityBackEndProjectForPilots", "src", "test", "resources",
    "pii-test-corpus.csv",
)
CORPUS = os.path.abspath(CORPUS)

TARGET_NEG = 300
TARGET_POS = 200

# ---------------------------------------------------------------- load
with open(CORPUS, newline="", encoding="utf-8") as f:
    reader = csv.reader(f)
    header = next(reader)
    rows = [r for r in reader if r and r[0].strip()]

max_id = max(int(r[0]) for r in rows)
existing_texts = {r[1] for r in rows}
neg = sum(1 for r in rows if r[2].strip().lower() == "false")
pos = len(rows) - neg

if len(rows) >= 500:
    print(f"Corpus already has {len(rows)} rows; nothing to do.")
    raise SystemExit(0)

need_neg = TARGET_NEG - neg
need_pos = TARGET_POS - pos
print(f"Current: {len(rows)} rows ({neg} neg / {pos} pos). "
      f"Adding {need_neg} neg + {need_pos} pos.")

new_rows = []
nid = max_id


def add(text, has_pii, ptype, desc):
    global nid
    if text in existing_texts:
        return False
    nid += 1
    existing_texts.add(text)
    new_rows.append([str(nid), text, "true" if has_pii else "false",
                     ptype, desc, "synthetic"])
    return True


# ---------------------------------------------------------------- negatives
def gen_gps():
    dec = random.choice([4, 5, 6, 7])
    lat = f"{random.uniform(-89, 89):.{dec}f}"
    lon = f"{random.uniform(-179, 179):.{dec}f}"
    s = f"{lat} {lon}"
    if random.random() < 0.35:
        s += f" {random.uniform(0, 3000):.1f}"
    return s, "GPS coordinates"


def gen_ean():
    v = str(random.randint(1, 9)) + "".join(random.choice("0123456789") for _ in range(12))
    return v, "EAN-13 barcode"


def gen_date():
    d, m, y = random.randint(1, 28), random.randint(1, 12), random.randint(2018, 2026)
    if random.random() < 0.5:
        return f"{y:04d}-{m:02d}-{d:02d}", "ISO date"
    return f"{d:02d}/{m:02d}/{y:04d}", "EU date"


def gen_datetime():
    base, _ = gen_date()
    return f"{base} {random.randint(0,23):02d}:{random.randint(0,59):02d}", "Timestamp"


def gen_decimal():
    dec = random.choice([1, 2, 3, 6, 10])
    v = f"{random.uniform(0, 2000):.{dec}f}"
    if random.random() < 0.25:
        v = "-" + v
    return v, "Nutritional decimal value"


def gen_leading_zeros():
    return "0" * random.randint(3, 6) + str(random.randint(1, 99999)), "Leading-zeros identifier"


def gen_slash():
    r3 = lambda: f"{random.randint(0,999):03d}"
    style = random.choice([1, 2, 3])
    if style == 1:
        return f"{r3()}/{r3()}/{r3()}", "Slash-separated identifier"
    if style == 2:
        return f"000/000/000/{random.randint(0,99):02d}", "Slash-separated product code"
    return f"{r3()}/{r3()}/{r3()}/{random.randint(0,99):02d}", "Hierarchical identifier"


def gen_hyphen():
    return f"{random.randint(10**6, 10**9)}-{random.randint(100, 9999)}", "Hyphen numeric identifier"


def gen_nutri():
    style = random.choice([1, 2, 3, 4, 5])
    if style == 1:
        return (f"Proteines: {random.uniform(0,30):.1f}g Glucides: "
                f"{random.uniform(0,90):.1f}g Lipides: {random.uniform(0,40):.1f}g",
                "Nutritional composition")
    if style == 2:
        return (f"pH: {random.uniform(3,7):.2f} / Aw: {random.uniform(0.7,0.99):.3f} / "
                f"Brix: {random.uniform(5,20):.1f}", "Physicochemical measurements")
    if style == 3:
        return (f"Energie (kJ): {random.randint(300,2000)} / Energie (kcal): "
                f"{random.randint(80,500)}", "Energy values")
    if style == 4:
        return (f"Temperature: -{random.randint(15,25)}.0 a +{random.randint(2,8)}.0 degres",
                "Storage temperature range")
    return (f"Poids net: {random.randint(50,900)}g / Poids brut: {random.randint(60,950)}g",
            "Weight information")


def gen_multifield():
    labref = f"LAB-{random.randint(2018,2026)}-{random.randint(0,9999):04d}"
    contaminant = random.choice(["Listeria", "Salmonella", "Aflatoxine B1",
                                 "Ochratoxine A", "Escherichia coli", "Plomb"])
    parts = [labref, contaminant]
    for gen in random.sample([gen_gps, gen_ean, gen_date, gen_decimal,
                              gen_leading_zeros, gen_slash], random.randint(2, 4)):
        parts.append(gen()[0])
    parts.append(random.choice(["conforme", "non conforme", "negatif", "<10 UFC/g"]))
    return ";".join(parts), "Multi-field lab/supply CSV row"


NEG_GENS = [
    (gen_gps, 30), (gen_ean, 25), (gen_date, 20), (gen_datetime, 15),
    (gen_decimal, 20), (gen_leading_zeros, 15), (gen_slash, 18),
    (gen_hyphen, 15), (gen_nutri, 20), (gen_multifield, 20),
]

produced = 0
for gen, count in NEG_GENS:
    made = 0
    guard = 0
    while made < count and produced < need_neg and guard < count * 50:
        guard += 1
        text, desc = gen()
        if add(text, False, "none", desc):
            made += 1
            produced += 1
# top up if any category underfilled due to collisions
while produced < need_neg:
    text, desc = random.choice([gen_gps, gen_ean, gen_decimal, gen_multifield])()
    if add(text, False, "none", desc):
        produced += 1

# ---------------------------------------------------------------- positives
# (a) emails -- any valid syntax is detected
FN = ["pierre", "marie", "jean", "sophie", "luc", "anna", "mario", "paul",
      "laura", "thomas", "julie", "marc", "claire", "hugo", "emma",
      "nicolas", "sarah", "david", "elena", "carlos"]
LN = ["durand", "martin", "bernard", "petit", "moreau", "schmidt", "rossi",
      "garcia", "lefevre", "dubois", "fontaine", "rousseau", "girard",
      "lambert", "fabre", "mercier", "blanc", "henry", "roux", "vidal"]
DOM = ["gmail.com", "yahoo.fr", "orange.fr", "outlook.com", "laposte.net",
       "labo-analyse.fr", "food-control.eu", "biosud.fr", "alimentari.it",
       "lebensmittel.de", "bodega.es", "controle-sanitaire.fr"]
ROLES = ["contact", "qualite", "export", "commande", "direction", "labo",
         "resultats", "service.client"]
COMPANIES = ["ferme-bio", "cooperative-laitiere", "usine-agro", "labo-nord",
             "analyses-food", "supply-eu", "agro-test"]

emails_made = 0
while emails_made < 45:
    style = random.random()
    if style < 0.55:
        e = f"{random.choice(FN)}.{random.choice(LN)}@{random.choice(DOM)}"
    elif style < 0.8:
        e = f"{random.choice(ROLES)}@{random.choice(COMPANIES)}.{random.choice(['fr','com','eu'])}"
    else:
        addr = f"{random.choice(FN)}.{random.choice(LN)}@{random.choice(DOM)}"
        e = random.choice([f"Contact: {addr}", f"Envoyer a {addr}",
                           f"Email responsable: {addr}"])
    if add(e, True, "email", "Synthetic email positive"):
        emails_made += 1

# (b) EU phones -- MR2 format variants of numbers confirmed detected in corpus
BASES = [
    "+33 6 12 34 56 78", "+33 1 42 68 53 00", "0044 20 7946 0958",
    "+49 30 12345678", "+32 2 555 12 34", "+34 91 123 45 67",
    "+39 06 1234 5678", "+31 20 123 4567", "+48 22 123 45 67",
    "+45 33 12 34 56", "+352 26 12 34 56", "0033 6 98 76 54 32",
    "+33 4 91 00 00 00",
]


def canonical(num):
    d = num.replace(" ", "")
    if d.startswith("00"):
        d = "+" + d[2:]
    return d  # like +33612345678


def variants(num):
    c = canonical(num)          # +CC...
    cc = c[1:]                  # digits after +
    out = [
        ".".join(c),                              # dotted-per-char? no -> build below
    ]
    # readable groupings
    body = cc
    out = [
        f"+{body}",                               # compact +
        f"00{body}",                              # compact 00
        f"(+{body[:2]}){body[2:]}",               # parenthesized CC (2-digit)
        "+" + ".".join([body[:2]] + [body[i:i+2] for i in range(2, len(body), 2)]),  # dotted
        "+" + "-".join([body[:2]] + [body[i:i+2] for i in range(2, len(body), 2)]),  # hyphen
        "00" + " ".join([body[:2]] + [body[i:i+2] for i in range(2, len(body), 2)]),  # spaced 00
    ]
    return out


phones_made = 0
cand = []
for b in BASES:
    cand.extend(variants(b))
random.shuffle(cand)
for e in cand:
    if phones_made >= 35:
        break
    if add(e, True, "phone_eu", "Synthetic EU phone (MR2 format variant)"):
        phones_made += 1

# (c) out-of-scope positives (false negatives by design; do not affect precision)
OOS = [
    ("Camille Blanc", "name", "French personal name"),
    ("Antoine Mercier", "name", "French personal name"),
    ("Dr. Helene Fabre", "name", "Name with title"),
    ("10 rue des Lilas 44000 Nantes", "address", "French postal address"),
    ("5 avenue de la Gare 67000 Strasbourg", "address", "French street address"),
    ("22 chemin du Moulin 31000 Toulouse", "address", "French postal address"),
    ("+1 305 555 0142", "phone_non_eu", "US phone Miami"),
    ("+81 6 1234 5678", "phone_non_eu", "Japanese phone Osaka"),
    ("+55 11 91234 5678", "phone_non_eu", "Brazilian phone Sao Paulo"),
    ("+91 80 1234 5678", "phone_non_eu", "Indian phone Bangalore"),
    ("+27 21 123 4567", "phone_non_eu", "South African phone Cape Town"),
    ("07 12 34 56 78", "phone_local", "French mobile local format"),
    ("02 40 12 34 56", "phone_local", "French landline local format"),
    ("NSS: 1 90 07 44 108 234 56", "national_id", "French social security number"),
    ("Passeport: 15CD67890", "national_id", "French passport number"),
    ("Adresse IP: 172.16.0.99", "ip_address", "IPv4 address"),
    ("Date naissance: 12/07/1969", "dob", "Date of birth"),
    ("Ne le 5 mai 1975 a Nantes", "dob", "Date of birth in text"),
    ("prenom [at] domaine [dot] com", "email_obfuscated", "Obfuscated email"),
    ("IBAN: IT60 X054 2811 1010 0000 0123 456", "financial", "Italian IBAN"),
]
oos_made = 0
for text, ptype, desc in OOS:
    if oos_made >= need_pos - 45 - 35:
        break
    if add(text, True, ptype, desc):
        oos_made += 1

# ---------------------------------------------------------------- write
all_rows = rows + new_rows
with open(CORPUS, "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow(header)
    w.writerows(all_rows)

fneg = sum(1 for r in all_rows if r[2].strip().lower() == "false")
fpos = len(all_rows) - fneg
print(f"Wrote {len(all_rows)} rows: {fneg} negative / {fpos} positive "
      f"(added {len(new_rows)}).")
