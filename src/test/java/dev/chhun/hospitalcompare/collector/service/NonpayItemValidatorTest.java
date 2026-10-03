package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.hira.dto.NonpayItemCode;
import dev.chhun.hospitalcompare.snapshot.dto.QualityIssue;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NonpayItemValidatorTest {

	private final NonpayItemValidator validator = new NonpayItemValidator();

	@Test
	void 코드와_날짜를_읽고_무기한은_9999년으로_둔다() {
		NonpayItemValidator.Result result = validator.validate(List.of(code("ABZ010001", "상급병실료/1인실", "20180402")));

		assertThat(result.records().getFirst().adtFrDd()).isEqualTo(LocalDate.of(2018, 4, 2));
		assertThat(result.records().getFirst().adtEndDd()).isEqualTo(LocalDate.of(9999, 12, 31));
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void 코드가_없으면_빼고_기록한다() {
		NonpayItemValidator.Result result = validator.validate(List.of(code(" ", "이름만", "20180402")));

		assertThat(result.records()).isEmpty();
		assertThat(result.issues()).extracting(QualityIssue::type).containsExactly(QualityIssueType.MISSING_REQUIRED);
	}

	@Test
	void 이름이_400자를_넘으면_잘라서_적재하고_기록한다() {
		NonpayItemValidator.Result result = validator.validate(List.of(code("ABZ010001", "가".repeat(401), "20180402")));

		assertThat(result.records().getFirst().npayKorNm()).hasSize(400);
		assertThat(result.issues()).extracting(QualityIssue::type).containsExactly(QualityIssueType.VALUE_TRUNCATED);
	}

	private static NonpayItemCode code(String npayCd, String name, String adtFrDd) {
		return new NonpayItemCode(npayCd, name, "1010A", "상급병실료", "1010A010", "1인실", "1010A010", "1인실",
				adtFrDd, "99991231");
	}

}
