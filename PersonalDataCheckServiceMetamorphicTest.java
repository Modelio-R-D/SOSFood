package org.eclipse.foodity.elasticsearch.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Metamorphic tests: automated generation of derived inputs from seed sets,
 * implementing the four metamorphic relations (MR1-MR4) formalized in the
 * ICTSS 2026 paper. The @MethodSource generators below produce 200+ derived
 * inputs programmatically (not hand-authored) and check that each relation
 * holds for the hard detector.
 */
class PersonalDataCheckServiceMetamorphicTest {

	private final PersonalDataCheckService service = new PersonalDataCheckService();

	private boolean flagsHard(String text) {
		try {
			service.validateHardPersonalDataOnly(
					new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
			return false;
		} catch (PersonalDataException e) {
			return true;
		}
	}

	// Confirmed-valid EU numbers (canonical international form), all detected.
	static final List<String> PHONE_SEEDS = List.of(
			"+33612345678", "+33142685300", "+442079460958", "+493012345678",
			"+3225551234", "+34911234567", "+390612345678", "+31201234567",
			"+48221234567", "+4533123456", "+35226123456", "+33698765432",
			"+33491000000");

	static final List<String> EMAIL_SEEDS = List.of(
			"jean.dupont@gmail.com", "marie.martin@yahoo.fr", "contact@entreprise-alimentaire.fr",
			"user.name+tag@domain.co.uk", "inspector@dgal.gouv.fr", "responsable.qualite@labo-analyse.com",
			"chef.equipe@usine.fr", "analyse@labo-central.org", "pierre.durand@orange.fr",
			"export@bodega-espanola.es");

	// Food-data fields that individually pass (used as MR1 context).
	static final List<String> NEG_POOL = List.of(
			"48.8566 2.3522", "3560070348985", "16/06/2021 16:53", "13.6857142857143",
			"0000000005", "000/045/012", "2023-01-15", "17024320-1817");

	// Production-derived multi-field negative rows (individually pass).
	static final List<String> PROD_ROWS = List.of(
			"LAB-2024-0156;Salmonella spp;negatif;48.8912 2.2387;16/06/2021 16:53;3560070348985",
			"PREP-001;Listeria monocytogenes;<10 UFC/g;6.0366893 -4.9784965;0000000015707/8",
			"000/045/012;Aflatoxine B1;0.025;ug/kg;conforme;2024-03-15",
			"8710398017901;Lait demi-ecreme UHT;Proteines: 3.2g;Lipides: 1.6g;00000089432",
			"17024320-1817-4a6e-89f3-e3203273be1f;resultat;13.6857142857143;mg/kg",
			"01/09/2025;2006108;VILKIJA;1.114",
			"01/09/2025;23026016;MEIRONISKIS;1.113",
			"01/09/2025;24609504SSSSSK;ZIBARTONIAI;3.72",
			"28/06/2024;2.31E+12;PAGIRIAI;0.984",
			"01/09/2025;23622773;KARMELAVA;0.588",
			"01/09/2025;24480198;DOTNUVA 1;1.107",
			"01/09/2025;2536895;KA;10.54");

	// ---- MR1: Positive Context Independence ----
	static Stream<Arguments> mr1Cases() {
		List<String> pii = new ArrayList<>();
		pii.addAll(PHONE_SEEDS);
		pii.addAll(EMAIL_SEEDS);
		List<Arguments> out = new ArrayList<>();
		for (int i = 0; i < pii.size(); i++) {
			String p = pii.get(i);
			String f0 = NEG_POOL.get(i % NEG_POOL.size());
			String f1 = NEG_POOL.get((i + 1) % NEG_POOL.size());
			String f2 = NEG_POOL.get((i + 2) % NEG_POOL.size());
			out.add(Arguments.of(p, f0 + ";" + f1 + ";" + p + ";" + f2));
		}
		return out.stream();
	}

	@ParameterizedTest(name = "MR1 [{0}] survives food-data context")
	@MethodSource("mr1Cases")
	void mr1_positiveContextIndependence(String seed, String embedded) {
		assertTrue(flagsHard(seed), "seed must be flagged: " + seed);
		assertTrue(flagsHard(embedded), "PII must survive context: " + embedded);
	}

	// ---- MR2: Phone Format Invariance ----
	static Stream<Arguments> mr2Cases() {
		List<Arguments> out = new ArrayList<>();
		for (String c : PHONE_SEEDS) {
			String d = c.substring(1);
			for (String v : List.of(
					c,
					"00" + d,
					"+" + group(d, " "),
					"+" + group(d, "."),
					"+" + group(d, "-"),
					"(+" + d.substring(0, 2) + ")" + d.substring(2))) {
				out.add(Arguments.of(c, v));
			}
		}
		return out.stream();
	}

	@ParameterizedTest(name = "MR2 [{1}] preserves detection")
	@MethodSource("mr2Cases")
	void mr2_phoneFormatInvariance(String seed, String variant) {
		assertTrue(flagsHard(variant), "format variant must be flagged: " + variant);
	}

	// ---- MR3: Negative Context Independence (pairwise composition) ----
	static Stream<Arguments> mr3Cases() {
		List<Arguments> out = new ArrayList<>();
		for (int i = 0; i < PROD_ROWS.size(); i++) {
			for (int j = i + 1; j < PROD_ROWS.size(); j++) {
				out.add(Arguments.of(PROD_ROWS.get(i) + ";" + PROD_ROWS.get(j)));
			}
		}
		return out.stream();
	}

	@ParameterizedTest(name = "MR3 composition stays pass")
	@MethodSource("mr3Cases")
	void mr3_negativeContextIndependence(String composed) {
		assertFalse(flagsHard(composed), "composition must not be flagged: " + composed);
	}

	// ---- MR4: Email Case Invariance ----
	static Stream<Arguments> mr4Cases() {
		List<Arguments> out = new ArrayList<>();
		for (String e : EMAIL_SEEDS) {
			int at = e.indexOf('@');
			String domainUpper = e.substring(0, at) + "@" + e.substring(at + 1).toUpperCase(Locale.ROOT);
			for (String v : List.of(e.toLowerCase(Locale.ROOT), e.toUpperCase(Locale.ROOT), mix(e), domainUpper)) {
				out.add(Arguments.of(e, v));
			}
		}
		return out.stream();
	}

	@ParameterizedTest(name = "MR4 [{1}] preserves detection")
	@MethodSource("mr4Cases")
	void mr4_emailCaseInvariance(String seed, String variant) {
		assertTrue(flagsHard(variant), "email case variant must be flagged: " + variant);
	}

	private static String group(String digits, String sep) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < digits.length(); i++) {
			if (i > 0 && i % 2 == 0) {
				sb.append(sep);
			}
			sb.append(digits.charAt(i));
		}
		return sb.toString();
	}

	private static String mix(String s) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			sb.append(i % 2 == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
		}
		return sb.toString();
	}
}
