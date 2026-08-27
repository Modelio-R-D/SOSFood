# Reproducibility — ICTSS 2026

Testing Domain-Specific Email and Phone Detection for GDPR Compliance in Cloud Food Data Lakes.

This directory contains the material to reproduce the evaluation (Section 5 of the paper).

## Contents

| Path | Description |
|------|-------------|
| `../FoodityBackEndProjectForPilots/src/test/resources/pii-test-corpus.csv` | Annotated corpus (500 samples: 300 negative / 200 positive) |
| `../FoodityBackEndProjectForPilots/src/main/java/org/eclipse/foodity/elasticsearch/service/PersonalDataCheckService.java` | Detection service (3-layer pipeline) |
| `../FoodityBackEndProjectForPilots/src/main/resources/keywords.txt` | 51 privacy keywords (Layer 3) |
| `../FoodityBackEndProjectForPilots/src/test/java/.../PersonalDataCheckServiceTest.java` | 15 unit tests |
| `../FoodityBackEndProjectForPilots/src/test/java/.../PersonalDataCheckServiceMetamorphicTest.java` | 207 generated metamorphic tests (MR1–MR4) |
| `../FoodityBackEndProjectForPilots/src/test/java/.../PersonalDataCheckServiceCorpusTest.java` | Corpus precision/recall assertion |
| `gen_corpus_500.py` | Reproducible corpus generator (fixed seed) |
| `eval_presidio.py` | Microsoft Presidio v2.2 comparison baseline |
| `requirements.txt` | Python dependencies for the Presidio baseline |

## Reproduce the detector results (Java)

From `FoodityBackEndProjectForPilots/`:

```bash
# Precision/recall on the corpus: expect FP=0 on 300 negatives, in-scope recall 99.3%
./mvnw test -Dtest=PersonalDataCheckServiceCorpusTest

# Metamorphic relations: expect 207 generated tests, all passing
./mvnw test -Dtest=PersonalDataCheckServiceMetamorphicTest

# Unit tests
./mvnw test -Dtest=PersonalDataCheckServiceTest
```

## Reproduce the Presidio comparison (Python)

Python 3.10–3.13 (Presidio 2.2.362 requires `<3.14`):

```bash
python -m venv .venv
. .venv/bin/activate        # Windows: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
python eval_presidio.py     # expect 28 false positives on food data
```

## Regenerate the corpus (optional)

```bash
python gen_corpus_500.py    # deterministic; idempotent once the corpus has 500 rows
```

## Expected headline numbers

| System | FP (food data) | In-scope precision | In-scope recall |
|--------|----------------|--------------------|-----------------|
| Ours | 0 / 300 | 1.000 (95% Wilson CI [0.99, 1.0]) | 0.993 |
| Presidio v2.2.362 | 28 / 300 | 0.833 | 0.993 |

Fisher's exact test on the false-positive difference: p < 1e-8.
