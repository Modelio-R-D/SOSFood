"""
Evaluate Microsoft Presidio v2.2 on the PII test corpus.
Compares phone and email detection only (same scope as our system).
"""
import csv
import os
import time
from presidio_analyzer import AnalyzerEngine
from presidio_analyzer.nlp_engine import NlpEngineProvider

# Use a simple NLP engine (no spaCy model needed for phone/email)
configuration = {
    "nlp_engine_name": "spacy",
    "models": [{"lang_code": "fr", "model_name": "fr_core_news_sm"}],
}

# Try without spaCy model first (Presidio can work with just recognizers)
analyzer = AnalyzerEngine()

_HERE = os.path.dirname(os.path.abspath(__file__))
_CANDIDATES = [
    os.path.join(_HERE, "pii-test-corpus.csv"),  # flat repository layout
    os.path.join(_HERE, "..", "FoodityBackEndProjectForPilots", "src",
                 "test", "resources", "pii-test-corpus.csv"),  # monorepo layout
]
CORPUS_PATH = next((os.path.abspath(p) for p in _CANDIDATES if os.path.exists(p)),
                   os.path.abspath(_CANDIDATES[0]))

# Only look for PHONE_NUMBER and EMAIL_ADDRESS (same scope as our system)
ENTITIES = ["PHONE_NUMBER", "EMAIL_ADDRESS"]

results = []

with open(CORPUS_PATH, "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        sample_id = int(row["id"])
        text = row["sample_text"]
        has_pii = row["ground_truth_has_pii"].lower() == "true"
        pii_type = row["pii_type"]
        
        # Run Presidio
        findings = analyzer.analyze(text=text, entities=ENTITIES, language="en")
        presidio_flagged = len(findings) > 0
        
        # Determine correctness
        # In-scope positives: email, phone_eu
        is_in_scope_positive = pii_type in ("email", "phone_eu")
        is_negative = not has_pii
        
        results.append({
            "id": sample_id,
            "text": text[:50],
            "ground_truth": has_pii,
            "pii_type": pii_type,
            "is_in_scope": is_in_scope_positive or is_negative,
            "presidio_flagged": presidio_flagged,
            "entities_found": [(e.entity_type, e.score) for e in findings]
        })

# Compute metrics
tp = sum(1 for r in results if r["ground_truth"] and r["presidio_flagged"])
fp = sum(1 for r in results if not r["ground_truth"] and r["presidio_flagged"])
fn_global = sum(1 for r in results if r["ground_truth"] and not r["presidio_flagged"])
tn = sum(1 for r in results if not r["ground_truth"] and not r["presidio_flagged"])

# In-scope metrics (only email + phone_eu as positives, negatives stay)
in_scope = [r for r in results if r["pii_type"] in ("email", "phone_eu", "none")]
tp_scope = sum(1 for r in in_scope if r["ground_truth"] and r["presidio_flagged"])
fn_scope = sum(1 for r in in_scope if r["ground_truth"] and not r["presidio_flagged"])
fp_scope = sum(1 for r in in_scope if not r["ground_truth"] and r["presidio_flagged"])

precision = tp / (tp + fp) if (tp + fp) > 0 else 0
recall_global = tp / (tp + fn_global) if (tp + fn_global) > 0 else 0
precision_scope = tp_scope / (tp_scope + fp_scope) if (tp_scope + fp_scope) > 0 else 0
recall_scope = tp_scope / (tp_scope + fn_scope) if (tp_scope + fn_scope) > 0 else 0

print("=" * 60)
print(f"PRESIDIO v2.2 EVALUATION ON PII CORPUS ({len(results)} samples)")
print("=" * 60)
print(f"\nEntities searched: {ENTITIES}")
print(f"\n--- Global metrics ---")
print(f"TP={tp}, FP={fp}, FN={fn_global}, TN={tn}")
print(f"Precision = {precision:.4f}")
print(f"Recall (global) = {recall_global:.4f}")
print(f"\n--- In-scope metrics (email + phone_eu only) ---")
print(f"TP={tp_scope}, FP={fp_scope}, FN={fn_scope}")
print(f"Precision (in-scope) = {precision_scope:.4f}")
print(f"Recall (in-scope) = {recall_scope:.4f}")

print(f"\n--- False Positives detail ---")
for r in results:
    if not r["ground_truth"] and r["presidio_flagged"]:
        print(f"  ID={r['id']}: '{r['text']}' -> {r['entities_found']}")

print(f"\n--- False Negatives (in-scope) ---")
for r in results:
    if r["pii_type"] in ("email", "phone_eu") and not r["presidio_flagged"]:
        print(f"  ID={r['id']}: '{r['text']}' -> NOT detected")

print(f"\n--- Detections on out-of-scope positives ---")
for r in results:
    if r["pii_type"] not in ("email", "phone_eu", "none") and r["presidio_flagged"]:
        print(f"  ID={r['id']} ({r['pii_type']}): '{r['text']}' -> {r['entities_found']}")
