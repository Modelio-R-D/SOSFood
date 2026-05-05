package org.eclipse.foodity.elasticsearch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Corpus-based evaluation of PersonalDataCheckService.
 * Computes precision, recall, F1-score, and confusion matrix
 * against an annotated corpus of 88 samples.
 *
 * Ground truth scope: any PII (email, phone, name, address, national ID, etc.)
 * System scope: emails + European international-format phone numbers only.
 *
 * This intentional scope mismatch reveals the system's coverage limitations
 * and is discussed in the ICTSS 2026 paper evaluation section.
 */
class PersonalDataCheckServiceCorpusTest {

	private final PersonalDataCheckService service = new PersonalDataCheckService();

	record CorpusSample(int id, String text, boolean groundTruthHasPii, String piiType, String description) {}

	record EvaluationResult(int tp, int fp, int tn, int fn, List<CorpusSample> falsePositives,
			List<CorpusSample> falseNegatives) {

		double precision() {
			return tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
		}

		double recall() {
			return tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
		}

		double f1Score() {
			double p = precision();
			double r = recall();
			return (p + r) == 0 ? 0.0 : 2 * p * r / (p + r);
		}

		double accuracy() {
			int total = tp + tn + fp + fn;
			return total == 0 ? 0.0 : (double) (tp + tn) / total;
		}

		double specificity() {
			return tn + fp == 0 ? 0.0 : (double) tn / (tn + fp);
		}
	}

	@Test
	void evaluateCorpusPrecisionRecall() {
		List<CorpusSample> corpus = loadCorpus();

		int tp = 0, fp = 0, tn = 0, fn = 0;
		List<CorpusSample> falsePositives = new ArrayList<>();
		List<CorpusSample> falseNegatives = new ArrayList<>();

		for (CorpusSample sample : corpus) {
			boolean systemFlagged = doesSystemFlag(sample.text());

			if (sample.groundTruthHasPii() && systemFlagged) {
				tp++;
			} else if (!sample.groundTruthHasPii() && !systemFlagged) {
				tn++;
			} else if (!sample.groundTruthHasPii() && systemFlagged) {
				fp++;
				falsePositives.add(sample);
			} else { // groundTruth=true && !systemFlagged
				fn++;
				falseNegatives.add(sample);
			}
		}

		EvaluationResult result = new EvaluationResult(tp, fp, tn, fn, falsePositives, falseNegatives);

		// Print evaluation report
		System.out.println("=".repeat(70));
		System.out.println("PII DETECTION CORPUS EVALUATION REPORT");
		System.out.println("=".repeat(70));
		System.out.println();
		System.out.println("Corpus size: " + corpus.size() + " samples");
		System.out.println("Ground truth positives: " + corpus.stream().filter(CorpusSample::groundTruthHasPii).count());
		System.out.println("Ground truth negatives: " + corpus.stream().filter(s -> !s.groundTruthHasPii()).count());
		System.out.println();
		System.out.println("-".repeat(70));
		System.out.println("CONFUSION MATRIX");
		System.out.println("-".repeat(70));
		System.out.printf("                    | System: FLAGGED | System: PASSED |%n");
		System.out.printf("  Ground Truth: PII |   TP = %-8d|   FN = %-8d|%n", tp, fn);
		System.out.printf("  Ground Truth: OK  |   FP = %-8d|   TN = %-8d|%n", fp, tn);
		System.out.println();
		System.out.println("-".repeat(70));
		System.out.println("METRICS");
		System.out.println("-".repeat(70));
		System.out.printf("  Precision:   %.4f  (of flagged items, how many are truly PII)%n", result.precision());
		System.out.printf("  Recall:      %.4f  (of true PII items, how many are flagged)%n", result.recall());
		System.out.printf("  F1-Score:    %.4f%n", result.f1Score());
		System.out.printf("  Accuracy:    %.4f%n", result.accuracy());
		System.out.printf("  Specificity: %.4f  (of non-PII items, how many are passed)%n", result.specificity());
		System.out.println();

		if (!falsePositives.isEmpty()) {
			System.out.println("-".repeat(70));
			System.out.println("FALSE POSITIVES (non-PII incorrectly flagged):");
			System.out.println("-".repeat(70));
			for (CorpusSample s : falsePositives) {
				System.out.printf("  [%d] \"%s\" (%s)%n", s.id(), s.text(), s.description());
			}
			System.out.println();
		}

		if (!falseNegatives.isEmpty()) {
			System.out.println("-".repeat(70));
			System.out.println("FALSE NEGATIVES (PII not detected):");
			System.out.println("-".repeat(70));
			for (CorpusSample s : falseNegatives) {
				System.out.printf("  [%d] \"%s\" [%s] (%s)%n", s.id(), s.text(), s.piiType(), s.description());
			}
			System.out.println();
		}

		// Print per-category breakdown
		System.out.println("-".repeat(70));
		System.out.println("FALSE NEGATIVE BREAKDOWN BY PII TYPE:");
		System.out.println("-".repeat(70));
		var fnByType = falseNegatives.stream()
				.collect(java.util.stream.Collectors.groupingBy(CorpusSample::piiType,
						java.util.stream.Collectors.counting()));
		long totalPiiSamples = corpus.stream().filter(CorpusSample::groundTruthHasPii).count();
		for (var entry : fnByType.entrySet()) {
			long typeTotal = corpus.stream()
					.filter(s -> s.groundTruthHasPii() && s.piiType().equals(entry.getKey()))
					.count();
			long typeMissed = entry.getValue();
			System.out.printf("  %-20s: %d/%d missed (recall=%.2f)%n",
					entry.getKey(), typeMissed, typeTotal,
					(double) (typeTotal - typeMissed) / typeTotal);
		}
		// Also show types with 100% recall
		var allPiiTypes = corpus.stream()
				.filter(CorpusSample::groundTruthHasPii)
				.map(CorpusSample::piiType)
				.distinct()
				.toList();
		for (String type : allPiiTypes) {
			if (!fnByType.containsKey(type)) {
				long typeTotal = corpus.stream()
						.filter(s -> s.groundTruthHasPii() && s.piiType().equals(type))
						.count();
				System.out.printf("  %-20s: 0/%d missed (recall=1.00)%n", type, typeTotal);
			}
		}

		System.out.println();
		System.out.println("=".repeat(70));
		System.out.println("INTERPRETATION FOR PAPER:");
		System.out.println("=".repeat(70));
		System.out.println("  The system achieves perfect precision (no false positives on food data)");
		System.out.println("  at the cost of limited recall (restricted to emails + EU int'l phones).");
		System.out.println("  This is a deliberate design choice: in a data lake context, false");
		System.out.println("  positives block legitimate uploads, while false negatives (missed PII)");
		System.out.println("  can be addressed by additional layers (manual review, keyword warnings).");
		System.out.println("=".repeat(70));

		// Assertions: validate that precision is perfect (no false positives on food data)
		assertEquals(0, fp, "System should have zero false positives on food/scientific data");
	}

	/**
	 * Separate test: evaluate only on the system's intended detection scope
	 * (emails + European international-format phone numbers).
	 * This gives the "in-scope" precision and recall.
	 */
	@Test
	void evaluateInScopeRecall() {
		List<CorpusSample> corpus = loadCorpus();

		// Filter to only in-scope PII types + all negatives
		List<CorpusSample> inScopePositives = corpus.stream()
				.filter(s -> s.groundTruthHasPii() &&
						(s.piiType().equals("email") || s.piiType().equals("phone_eu")))
				.toList();

		int detected = 0;
		int missed = 0;
		List<CorpusSample> missedSamples = new ArrayList<>();

		for (CorpusSample sample : inScopePositives) {
			if (doesSystemFlag(sample.text())) {
				detected++;
			} else {
				missed++;
				missedSamples.add(sample);
			}
		}

		System.out.println();
		System.out.println("=".repeat(70));
		System.out.println("IN-SCOPE EVALUATION (emails + EU international phones only)");
		System.out.println("=".repeat(70));
		System.out.printf("  In-scope samples: %d%n", inScopePositives.size());
		System.out.printf("  Detected (TP):    %d%n", detected);
		System.out.printf("  Missed (FN):      %d%n", missed);
		System.out.printf("  In-scope recall:  %.4f%n", (double) detected / inScopePositives.size());
		System.out.println();

		if (!missedSamples.isEmpty()) {
			System.out.println("  Missed in-scope samples:");
			for (CorpusSample s : missedSamples) {
				System.out.printf("    [%d] \"%s\" (%s)%n", s.id(), s.text(), s.description());
			}
		}

		// In-scope recall should be at least 90%
		double inScopeRecall = (double) detected / inScopePositives.size();
		assert inScopeRecall >= 0.90 : "In-scope recall should be at least 90%, got " + inScopeRecall;
	}

	private boolean doesSystemFlag(String text) {
		try {
			service.validateHardPersonalDataOnly(
					new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
			return false; // no exception = not flagged
		} catch (PersonalDataException e) {
			return true; // exception = flagged as PII
		}
	}

	private List<CorpusSample> loadCorpus() {
		List<CorpusSample> samples = new ArrayList<>();
		try (InputStream is = getClass().getResourceAsStream("/pii-test-corpus.csv");
				BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

			String line;
			boolean header = true;
			while ((line = reader.readLine()) != null) {
				if (header) {
					header = false;
					continue;
				}
				if (line.isBlank()) continue;

				// Parse CSV line (simple: split on first 4 commas)
				String[] parts = parseCsvLine(line);
				if (parts.length < 5) continue;

				int id = Integer.parseInt(parts[0].trim());
				String text = parts[1].trim();
				boolean hasPii = Boolean.parseBoolean(parts[2].trim());
				String piiType = parts[3].trim();
				String description = parts[4].trim();

				samples.add(new CorpusSample(id, text, hasPii, piiType, description));
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to load test corpus", e);
		}
		return samples;
	}

	/**
	 * Simple CSV parser that handles the corpus format.
	 * Splits on commas but respects that sample_text field may not contain commas
	 * (our corpus uses simple text without embedded commas).
	 */
	private String[] parseCsvLine(String line) {
		// Format: id,sample_text,ground_truth,pii_type,description
		// Split into exactly 5 fields by finding first 4 commas
		String[] result = new String[5];
		int fieldIndex = 0;
		int start = 0;

		for (int i = 0; i < line.length() && fieldIndex < 4; i++) {
			if (line.charAt(i) == ',') {
				result[fieldIndex] = line.substring(start, i);
				fieldIndex++;
				start = i + 1;
			}
		}
		result[fieldIndex] = line.substring(start);
		return result;
	}
}
