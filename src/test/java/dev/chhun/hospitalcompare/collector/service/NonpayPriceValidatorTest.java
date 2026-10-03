package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.chhun.hospitalcompare.hira.dto.NonpayHospPrice;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayPriceRecord;
import dev.chhun.hospitalcompare.snapshot.dto.QualityIssue;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NonpayPriceValidatorTest {

	private final NonpayPriceValidator validator = new NonpayPriceValidator();

	@Test
	void 정상_행은_원_단위_숫자와_날짜로_바꾼다() {
		NonpayPriceValidator.Result result = validator.validate("ABZ010001",
				List.of(row("Y-A", "ABZ010001", "285000", "355000", "20260903")));

		assertThat(result.records()).containsExactly(new NonpayPriceRecord("ABZ010001", "Y-A", 285000, 355000,
				"01", "110000", "110001", LocalDate.of(2026, 9, 3)));
		assertThat(result.issues()).isEmpty();
		assertThat(result.excluded()).isZero();
	}

	@Test
	void 필수값이_없으면_빼고_기록한다() {
		NonpayPriceValidator.Result result = validator.validate("ABZ010001", List.of(
				row(null, "ABZ010001", "1", "1", "20260903"),
				row("Y-B", "ABZ010001", " ", "1", "20260903")));

		assertThat(result.records()).isEmpty();
		assertThat(result.excluded()).isEqualTo(2);
		assertThat(result.issues()).extracting(QualityIssue::type)
				.containsOnly(QualityIssueType.MISSING_REQUIRED);
	}

	@Test
	void 요청한_항목과_다른_코드가_오면_뺀다() {
		NonpayPriceValidator.Result result = validator.validate("ABZ010001",
				List.of(row("Y-A", "HE1180000", "1", "1", "20260903")));

		assertThat(result.excluded()).isEqualTo(1);
		assertThat(result.issues().getFirst().rawValue()).contains("ABZ010001").contains("HE1180000");
	}

	@Test
	void 가격이_숫자가_아니거나_0_이하거나_최소가_최대보다_크면_뺀다() {
		NonpayPriceValidator.Result result = validator.validate("ABZ010001", List.of(
				row("Y-A", "ABZ010001", "N/A", "1", "20260903"),
				row("Y-B", "ABZ010001", "0", "100", "20260903"),
				row("Y-C", "ABZ010001", "900000", "800000", "20260903")));

		assertThat(result.records()).isEmpty();
		assertThat(result.excluded()).isEqualTo(3);
		assertThat(result.issues()).extracting(QualityIssue::type).containsOnly(QualityIssueType.INVALID_PRICE);
	}

	@Test
	void 소수_가격은_반올림한다() {
		NonpayPriceValidator.Result result = validator.validate("ABZ010001",
				List.of(row("Y-A", "ABZ010001", "1000.4", "1000.5", "20260903")));

		assertThat(result.records().getFirst().minPrice()).isEqualTo(1000);
		assertThat(result.records().getFirst().maxPrice()).isEqualTo(1001);
	}

	@Test
	void 적용일_형식이_틀리면_날짜만_비우고_적재한다() {
		NonpayPriceValidator.Result result = validator.validate("ABZ010001", List.of(
				row("Y-A", "ABZ010001", "1", "1", "2026-09-03"),
				row("Y-B", "ABZ010001", "1", "1", "20260931"),
				row("Y-C", "ABZ010001", "1", "1", null)));

		assertThat(result.records()).extracting(NonpayPriceRecord::adtFrDd).containsOnlyNulls();
		assertThat(result.excluded()).isZero();
		assertThat(result.issues()).extracting(QualityIssue::type).containsExactly(
				QualityIssueType.INVALID_DATE, QualityIssueType.INVALID_DATE);
	}

	private static NonpayHospPrice row(String ykiho, String npayCd, String min, String max, String adtFrDd) {
		return new NonpayHospPrice(ykiho, "가상병원", "01", "110000", "110001", npayCd, min, max, adtFrDd);
	}

	@Test
	void 지수가_터무니없이_큰_가격은_오래_걸리지_않고_뺀다() {
		NonpayPriceValidator.Result result = assertTimeoutPreemptively(Duration.ofSeconds(2),
				() -> validator.validate("ABZ010001", List.of(row("Y-A", "ABZ010001", "1E20000000", "1E20000000", "20260903"))));

		assertThat(result.records()).isEmpty();
		assertThat(result.excluded()).isEqualTo(1);
		assertThat(result.issues()).extracting(QualityIssue::type).containsOnly(QualityIssueType.INVALID_PRICE);
	}

}
