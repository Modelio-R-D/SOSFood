# SOSFood

Testing Domain-Specific Email and Phone Detection for GDPR Compliance in Cloud Food Data Lakes.

This repository contains the material needed to reproduce the evaluation reported in Section 5 of the paper (ICTSS 2026): a Java personal data detector for email addresses and phone numbers, its test suites, a labelled test corpus, and a comparison against Microsoft Presidio.

## Contents

| File | Description |
| --- | --- |
| `PersonalDataCheckService.java` | Detection service for emails and phone numbers |
| `FileProcessingException.java` | Exception for file processing errors |
| `PersonalDataException.java` | Exception raised when personal data is detected |
| `PersonalDataWarningConfirmationRequiredException.java` | Exception raised when a warning requires user confirmation |
| `PersonalDataCheckServiceTest.java` | Unit tests |
| `PersonalDataCheckServiceCorpusTest.java` | Corpus-based evaluation test |
| `PersonalDataCheckServiceMetamorphicTest.java` | Metamorphic tests |
| `keywords.txt` | Domain keyword list used by the detector |
| `pii-test-corpus.csv` | Test corpus |
| `gen_corpus_500.py` | Corpus generator |
| `eval_presidio.py` | Presidio baseline evaluation script |
| `requirements.txt` | Python dependencies for the Presidio baseline |
| `pom.xml` | Maven configuration |
| `LICENSE` | License terms |

## Requirements

- Java and Maven (the `./mvnw` wrapper is used in the commands below)
- Python 3.10 to 3.13 (Presidio 2.2.362 requires Python below 3.14)

## Reproducing the results

### 1. Java detector tests

1. Place the Java sources in a Maven project that includes these dependencies:
   - `spring-boot-starter-test`
   - Google `libphonenumber`
   - Lombok
2. Put `keywords.txt` on the main classpath.
3. Put `pii-test-corpus.csv` on the test classpath.
4. Run the three test suites:

```bash
./mvnw test -Dtest=PersonalDataCheckServiceCorpusTest
./mvnw test -Dtest=PersonalDataCheckServiceMetamorphicTest
./mvnw test -Dtest=PersonalDataCheckServiceTest
```

Expected results:

- Corpus test: 0 false positives on the 300 negative samples, and 99.3% recall on in-scope samples.
- Metamorphic test: all 207 generated tests pass.
- Unit tests: all pass.

### 2. Presidio comparison

```bash
pip install -r requirements.txt
python eval_presidio.py
```

The script should report 28 false positives on food data.

### 3. Corpus regeneration (optional)

```bash
python gen_corpus_500.py
```

The generator is deterministic and idempotent once the corpus contains 500 rows.

## Expected headline numbers

| System | False positives (food data) | In-scope precision | In-scope recall |
| --- | --- | --- | --- |
| This work | 0 / 300 | 1.000 (95% Wilson CI [0.99, 1.0]) | 0.993 |
| Presidio v2.2.362 | 28 / 300 | 0.833 | 0.993 |

Fisher's exact test on the false-positive difference gives p < 1e-8.

## License

## License

This project is licensed under the GNU General Public License v3.0. See the [LICENSE](LICENSE) file for details.
