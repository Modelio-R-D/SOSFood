package org.eclipse.foodity.elasticsearch.service;

import java.util.List;

public class PersonalDataWarningConfirmationRequiredException extends RuntimeException {

	private final List<String> suspectedKeywords;

	public PersonalDataWarningConfirmationRequiredException(String message, List<String> suspectedKeywords) {
		super(message);
		this.suspectedKeywords = suspectedKeywords;
	}

	public List<String> getSuspectedKeywords() {
		return suspectedKeywords;
	}
}
