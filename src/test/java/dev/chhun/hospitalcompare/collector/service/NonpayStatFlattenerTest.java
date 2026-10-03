package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.nonpay.dto.NonpayStatRecord;
import dev.chhun.hospitalcompare.nonpay.entity.StatDimension;
import dev.chhun.hospitalcompare.snapshot.dto.QualityIssue;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NonpayStatFlattenerTest {

	private final NonpayStatFlattener flattener = new NonpayStatFlattener();

	@Test
	void 칸_이름의_접미사로_세로로_펼친다() {
		NonpayStatFlattener.Result result = flattener.flatten(StatDimension.SIDO, List.of(Map.of(
				"npayCd", "ABZ010001", "stdDate", "20260903",
				"prcMinSl", "150000", "prcMaxSl", "540000", "prcAvgSl", "260000", "middAvgSl", "250000",
				"prcMinSj", "180000", "prcMaxSj", "180000", "prcAvgSj", "180000", "middAvgSj", "180000")));

		assertThat(result.records()).containsExactlyInAnyOrder(
				new NonpayStatRecord("ABZ010001", StatDimension.SIDO, "Sl", 150000L, 540000L, 260000L, 250000L,
						LocalDate.of(2026, 9, 3)),
				new NonpayStatRecord("ABZ010001", StatDimension.SIDO, "Sj", 180000L, 180000L, 180000L, 180000L,
						LocalDate.of(2026, 9, 3)));
		assertThat(result.npayCds()).containsExactly("ABZ010001");
		assertThat(result.issues()).isEmpty();
	}

	@Test
	void 네_값이_모두_0인_칸은_저장하지_않는다() {
		NonpayStatFlattener.Result result = flattener.flatten(StatDimension.CL_TYPE, List.of(Map.of(
				"npayCd", "ABZ010001",
				"prcMinRecu", "0", "prcMaxRecu", "0", "prcAvgRecu", "0", "middAvgRecu", "0",
				"prcMinAll", "20000", "prcMaxAll", "540000", "prcAvgAll", "210000", "middAvgAll", "200000")));

		assertThat(result.records()).extracting(NonpayStatRecord::dimKey).containsExactly("All");
	}

	@Test
	void 모르는_접미사는_한_번만_기록하고_버린다() {
		NonpayStatFlattener.Result result = flattener.flatten(StatDimension.SIDO, List.of(Map.of(
				"npayCd", "HE1180000",
				"prcMinXx", "1", "prcMaxXx", "1", "prcAvgXx", "1", "middAvgXx", "1",
				"prcMinSl", "1", "prcMaxSl", "1", "prcAvgSl", "1", "middAvgSl", "1")));

		assertThat(result.records()).extracting(NonpayStatRecord::dimKey).containsExactly("Sl");
		assertThat(result.issues()).extracting(QualityIssue::type).containsExactly(QualityIssueType.UNKNOWN_STAT_KEY);
		assertThat(result.issues().getFirst().rawValue()).contains("Xx");
	}

	@Test
	void 숫자가_아닌_값은_그_칸만_비우고_기록한다() {
		NonpayStatFlattener.Result result = flattener.flatten(StatDimension.SIDO, List.of(Map.of(
				"npayCd", "HE1180000",
				"prcMinPs", "300000", "prcMaxPs", "700000", "prcAvgPs", "N/A", "middAvgPs", "450000")));

		assertThat(result.records()).containsExactly(new NonpayStatRecord("HE1180000", StatDimension.SIDO, "Ps",
				300000L, 700000L, null, 450000L, null));
		assertThat(result.issues()).extracting(QualityIssue::type).containsExactly(QualityIssueType.INVALID_PRICE);
	}

	@Test
	void 코드가_없는_항목은_빼고_기록한다() {
		NonpayStatFlattener.Result result = flattener.flatten(StatDimension.CL_TYPE,
				List.of(Map.of("prcMinAll", "1")));

		assertThat(result.records()).isEmpty();
		assertThat(result.npayCds()).isEmpty();
		assertThat(result.issues()).extracting(QualityIssue::type).containsExactly(QualityIssueType.MISSING_REQUIRED);
	}

}
