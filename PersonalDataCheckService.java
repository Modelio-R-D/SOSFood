package org.eclipse.foodity.elasticsearch.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PersonalDataCheckService {
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
	private static final Pattern PHONE_CANDIDATE_PATTERN = Pattern
			.compile("(?<![\\p{L}\\p{N}])(?:\\+?[0-9][0-9\\s()./-]{8,}[0-9])(?![\\p{L}\\p{N}])");
	private static final Pattern DATE_LIKE_PATTERN = Pattern
			.compile("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}$|^\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}$");
	private static final Pattern DATE_TIME_LIKE_PATTERN = Pattern.compile(
			"^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}(?:\\s+\\d{1,2}(?::\\d{2})?)?$|^\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}(?:\\s+\\d{1,2}(?::\\d{2})?)?$");
	private static final Pattern COORDINATE_TUPLE_PATTERN = Pattern
			.compile("^[+\\-]?\\d{1,3}\\.\\d+(?:[\\s,;]+[+\\-]?\\d{1,3}\\.\\d+)+$");
	private static final Pattern ID_LIKE_NUMERIC_HYPHEN_PATTERN = Pattern.compile("^\\d{7,}-\\d{3,}$");
	private static final Pattern ID_LIKE_NUMERIC_SLASH_PATTERN = Pattern.compile("^\\d{3,}(?:/\\d+)+$");
	private static final Pattern ID_LIKE_NUMERIC_SLASH_PATH_PATTERN = Pattern.compile("^(?:\\d{3,}/){2,}\\d{1,}$");
	private static final Pattern DECIMAL_NUMBER_PATTERN = Pattern.compile("^[+\\-]?\\d+\\.\\d+$");
	private static final Pattern LEADING_ZEROS_IDENTIFIER_PATTERN = Pattern.compile("^0{3,}\\d+$");
	private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();
	private static final Set<String> EUROPEAN_REGIONS = Set.of(
			"AT", "BE", "BG", "CH", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GB", "GR", "HR", "HU",
			"IE", "IS", "IT", "LI", "LT", "LU", "LV", "MT", "NL", "NO", "PL", "PT", "RO", "SE", "SI", "SK");

	private static final String KEYWORDS_FILE = "/keywords.txt";
	private final List<String> keywords = new ArrayList<>();
	private final List<Pattern> keywordPatterns = new ArrayList<>();

	public void validateForUpload(MultipartFile file, boolean keywordWarningsConfirmed) {
		try (InputStream inputStream = file.getInputStream()) {
			validateForUpload(inputStream, keywordWarningsConfirmed);
		} catch (IOException e) {
			throw new FileProcessingException("Unable to read file for personal data check.", e);
		}
	}

	public boolean checkForPersonalData(MultipartFile file) {
		validateForUpload(file, false);
		return false;
	}

	public boolean checkForPersonalData(InputStream inputStream) {
		validateForUpload(inputStream, false);
		return false;
	}

	public void validateForUpload(InputStream inputStream, boolean keywordWarningsConfirmed) {
		PersonalDataScanResult scanResult = scanForPersonalData(inputStream);
		if (scanResult.hasHardPersonalData) {
			throw new PersonalDataException(
					"Personal data detected (email/phone). Please remove all personal data before uploading this file.");
		}
		if (!keywordWarningsConfirmed && !scanResult.suspectedKeywords.isEmpty()) {
			throw new PersonalDataWarningConfirmationRequiredException(
					"Potential personal data detected based on suspicious keywords. Please confirm to continue.",
					new ArrayList<>(scanResult.suspectedKeywords));
		}
	}

	public void validateHardPersonalDataOnly(InputStream inputStream) {
		PersonalDataScanResult scanResult = scanForPersonalData(inputStream);
		if (scanResult.hasHardPersonalData) {
			throw new PersonalDataException(
					"Personal data detected (email/phone). Please remove all personal data before uploading this file.");
		}
	}

	private PersonalDataScanResult scanForPersonalData(InputStream inputStream) {
		boolean hasHardPersonalData = false;
		Set<String> suspectedKeywords = new LinkedHashSet<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			String line;
			while ((line = reader.readLine()) != null) {
				boolean telephonePatternFound = containsLikelyPhoneNumber(line);
				boolean emailPatternFound = EMAIL_PATTERN.matcher(line).find();
				if (telephonePatternFound || emailPatternFound) {
					hasHardPersonalData = true;
				}
				suspectedKeywords.addAll(findSuspiciousKeywords(line));
				if (hasHardPersonalData) {
					break;
				}
			}
		} catch (IOException e) {
			throw new FileProcessingException("Unable to read stream for personal data check.", e);
		}
		return new PersonalDataScanResult(hasHardPersonalData, suspectedKeywords);
	}

	private boolean containsLikelyPhoneNumber(String line) {
		Matcher matcher = PHONE_CANDIDATE_PATTERN.matcher(line);
		while (matcher.find()) {
			String candidate = matcher.group();
			if (isLikelyPhoneCandidate(candidate)) {
				return true;
			}
		}
		return false;
	}

	private boolean isLikelyPhoneCandidate(String candidate) {
		if (candidate == null) {
			return false;
		}
		String trimmed = candidate.trim();
		if (DATE_LIKE_PATTERN.matcher(trimmed).matches()) {
			return false;
		}
		if (DATE_TIME_LIKE_PATTERN.matcher(trimmed).matches()) {
			return false;
		}
		if (COORDINATE_TUPLE_PATTERN.matcher(trimmed).matches()) {
			return false;
		}
		if (ID_LIKE_NUMERIC_HYPHEN_PATTERN.matcher(trimmed).matches()) {
			return false;
		}
		if (ID_LIKE_NUMERIC_SLASH_PATTERN.matcher(trimmed).matches()) {
			return false;
		}
		if (ID_LIKE_NUMERIC_SLASH_PATH_PATTERN.matcher(trimmed).matches()) {
			return false;
		}
		if (DECIMAL_NUMBER_PATTERN.matcher(trimmed).matches()) {
			return false;
		}
		if (LEADING_ZEROS_IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
			return false;
		}

		String normalized = normalizeToInternationalPhone(trimmed);
		if (normalized == null) {
			return false;
		}

		String digitsOnly = normalized.replaceAll("\\D", "");
		if (digitsOnly.length() < 10 || digitsOnly.length() > 15) {
			return false;
		}

		try {
			PhoneNumber phoneNumber = PHONE_NUMBER_UTIL.parse(normalized, "ZZ");
			if (!PHONE_NUMBER_UTIL.isValidNumber(phoneNumber)) {
				return false;
			}
			String regionCode = PHONE_NUMBER_UTIL.getRegionCodeForNumber(phoneNumber);
			if (regionCode == null || !EUROPEAN_REGIONS.contains(regionCode)) {
				return false;
			}
			PhoneNumberUtil.PhoneNumberType type = PHONE_NUMBER_UTIL.getNumberType(phoneNumber);
			return type == PhoneNumberUtil.PhoneNumberType.FIXED_LINE
					|| type == PhoneNumberUtil.PhoneNumberType.MOBILE
					|| type == PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE;
		} catch (NumberParseException e) {
			return false;
		}
	}

	private String normalizeToInternationalPhone(String candidate) {
		if (candidate == null) {
			return null;
		}
		String compact = candidate.replaceAll("[\\s()./-]", "");
		if (compact.startsWith("00")) {
			compact = "+" + compact.substring(2);
		}
		if (!compact.startsWith("+")) {
			return null;
		}
		if (!compact.matches("^\\+[0-9]{8,15}$")) {
			return null;
		}
		return compact;
	}

	private Set<String> findSuspiciousKeywords(String line) {
		Set<String> foundKeywords = new LinkedHashSet<>();
		String normalizedLine = normalizeForComparison(line);
		List<String> loadedKeywords = getKeywords();
		for (int i = 0; i < loadedKeywords.size(); i++) {
			Pattern keywordPattern = keywordPatterns.get(i);
			Matcher matcher = keywordPattern.matcher(normalizedLine);
			if (matcher.find()) {
				foundKeywords.add(loadedKeywords.get(i));
			}
		}
		return foundKeywords;
	}

	protected List<String> getKeywords() {
		if (keywords.isEmpty()) {
			try (InputStream inputStream = getClass().getResourceAsStream(KEYWORDS_FILE)) {
				if (inputStream == null) {
					throw new FileProcessingException("Unable to load keywords file for personal data check.");
				}
				List<String> loadedKeywords = new BufferedReader(new InputStreamReader(inputStream)).lines()
						.map(String::trim)
						.filter(keyword -> !keyword.isEmpty())
						.map(this::normalizeForComparison)
						.distinct()
						.collect(Collectors.toList());
				keywords.addAll(loadedKeywords);
				keywordPatterns.addAll(
						loadedKeywords.stream().map(this::buildKeywordPattern).collect(Collectors.toList()));
			} catch (IOException e) {
				throw new FileProcessingException("Unable to load keywords file for personal data check.", e);
			}
		}
		return keywords;
	}

	private Pattern buildKeywordPattern(String keyword) {
		String escapedKeyword = Pattern.quote(keyword);
		String regex = "(?<![\\p{L}\\p{N}])" + escapedKeyword + "(?![\\p{L}\\p{N}])";
		return Pattern.compile(regex);
	}

	private String normalizeForComparison(String value) {
		if (value == null) {
			return "";
		}
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "")
				.toLowerCase(Locale.ROOT)
				.trim();
		return normalized;
	}

	private static class PersonalDataScanResult {
		private final boolean hasHardPersonalData;
		private final Set<String> suspectedKeywords;

		private PersonalDataScanResult(boolean hasHardPersonalData, Set<String> suspectedKeywords) {
			this.hasHardPersonalData = hasHardPersonalData;
			this.suspectedKeywords = suspectedKeywords;
		}
	}
}
