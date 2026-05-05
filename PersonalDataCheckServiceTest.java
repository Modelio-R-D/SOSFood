package org.eclipse.foodity.elasticsearch.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PersonalDataCheckServiceTest {

	private final PersonalDataCheckService service = new PersonalDataCheckService();

	@Test
	void shouldNotFlagCoordinateTupleAsPhoneNumber() {
		String content = "6.0366893 -4.9784965 54.0 3.0";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldNotFlagUuidLikeNumericFragmentAsPhoneNumber() {
		String content = "17024320-1817-4a6e-89f3-e3203273be1f";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldNotFlagDateTimeFragmentAsPhoneNumber() {
		String content = "16/06/2021 16:53";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldNotFlagBarcodeLikeNumericIdentifierAsPhoneNumber() {
		String content = "0000000000017";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldNotFlagSlashSeparatedNumericIdentifierAsPhoneNumber() {
		String content = "000/000/000/0017";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldNotFlagSlashPathNumericIdentifierAsPhoneNumber() {
		String content = "000/000/000/5";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldNotFlagSlashTailNumericIdentifierAsPhoneNumber() {
		String content = "0000000015707/8";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldNotFlagDecimalNumericValueAsPhoneNumber() {
		String content = "13.6857142857143";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldNotFlagLeadingZerosIdentifierAsPhoneNumber() {
		String content = "0000000005";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldFlagRealPhoneNumber() {
		String content = "contact: +33 6 12 34 56 78";

		assertThrows(PersonalDataException.class, () -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldNotFlagLocalPhoneNumberWithoutCountryPrefix() {
		String content = "0612345678";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldNotFlagSlashSeparatedLocalPhoneWithoutCountryPrefix() {
		String content = "06/12/34/56/78";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldFlagEuropeanInternationalPhoneNumber() {
		String content = "0044 20 7946 0958";

		assertThrows(PersonalDataException.class, () -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldNotFlagNonEuropeanInternationalPhoneNumber() {
		String content = "+1 202 555 0123";

		assertDoesNotThrow(() -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void shouldFlagRealEmailAddress() {
		String content = "mail=test.user@foodity.org";

		assertThrows(PersonalDataException.class, () -> service
				.validateHardPersonalDataOnly(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
	}
}
