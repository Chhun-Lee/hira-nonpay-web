package dev.chhun.hospitalcompare.collector.runner;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.chhun.hospitalcompare.collector.dto.CollectResult;
import dev.chhun.hospitalcompare.collector.dto.NonpayCollectResult;
import dev.chhun.hospitalcompare.collector.service.HospitalCollector;
import dev.chhun.hospitalcompare.collector.service.NonpayCollector;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class CollectorRunnerTest {

	private final HospitalCollector hospitalCollector = mock(HospitalCollector.class);
	private final NonpayCollector nonpayCollector = mock(NonpayCollector.class);
	private final CollectorRunner runner = new CollectorRunner(hospitalCollector, nonpayCollector);

	@Test
	void 인자가_없으면_전국_병원_목록을_수집한다() {
		when(hospitalCollector.collect(any())).thenReturn(hospitalResult(SnapshotStatus.ACTIVE, null));

		runner.run(new DefaultApplicationArguments());

		verify(hospitalCollector).collect(null);
		verify(nonpayCollector, never()).collect(anyList());
	}

	@Test
	void sgguCd를_주면_그_시군구를_수집한다() {
		when(hospitalCollector.collect(any())).thenReturn(hospitalResult(SnapshotStatus.ACTIVE, null));

		runner.run(new DefaultApplicationArguments("--sgguCd=110001"));

		verify(hospitalCollector).collect("110001");
	}

	@Test
	void 병원_목록_수집이_실패하면_예외로_끝내_종료_코드를_남긴다() {
		when(hospitalCollector.collect(any())).thenReturn(hospitalResult(SnapshotStatus.FAILED, "건수 급감"));

		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("건수 급감");
	}

	@Test
	void 시군구를_둘_이상_주면_거절한다() {
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--sgguCd=110001", "--sgguCd=110002")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void target이_nonpay면_비급여_전_항목을_수집한다() {
		when(nonpayCollector.collect(anyList())).thenReturn(nonpayResult(SnapshotStatus.ACTIVE, null));

		runner.run(new DefaultApplicationArguments("--target=nonpay"));

		verify(nonpayCollector).collect(List.of());
		verify(hospitalCollector, never()).collect(any());
	}

	@Test
	void items를_주면_공백과_중복을_정리해_시험_실행한다() {
		when(nonpayCollector.collect(anyList())).thenReturn(nonpayResult(SnapshotStatus.TRIAL, "시험 실행"));

		runner.run(new DefaultApplicationArguments("--target=nonpay", "--items= ABZ010001,HE1180000,ABZ010001 "));

		verify(nonpayCollector).collect(List.of("ABZ010001", "HE1180000"));
	}

	@Test
	void 비급여_시험_실행은_정상_종료하고_실패는_예외로_끝낸다() {
		when(nonpayCollector.collect(anyList())).thenReturn(nonpayResult(SnapshotStatus.TRIAL, "시험 실행"));
		assertThatCode(() -> runner.run(new DefaultApplicationArguments("--target=nonpay", "--items=ABZ010001")))
				.doesNotThrowAnyException();

		when(nonpayCollector.collect(anyList())).thenReturn(nonpayResult(SnapshotStatus.FAILED, "부분 수집 의심"));
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--target=nonpay")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("부분 수집 의심");
	}

	@Test
	void 오타난_옵션은_거절해_엉뚱한_전국_수집을_막는다() {
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--sggucd=110001")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("sggucd");
		verify(hospitalCollector, never()).collect(any());
	}

	@Test
	void 이름_없는_인자는_거절한다() {
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("110001")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 점이_있는_옵션은_Spring_설정으로_보고_통과시킨다() {
		when(nonpayCollector.collect(anyList())).thenReturn(nonpayResult(SnapshotStatus.ACTIVE, null));

		runner.run(new DefaultApplicationArguments("--target=nonpay", "--hira.nonpay.max-concurrency=8"));

		verify(nonpayCollector).collect(List.of());
	}

	@Test
	void 대상에_맞지_않는_옵션_조합은_거절한다() {
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--target=nonpay", "--sgguCd=110001")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--items=ABZ010001")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--target=nonpai")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--target=nonpay", "--items= , ")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static CollectResult hospitalResult(SnapshotStatus status, String failureReason) {
		return new CollectResult("전국", 1L, status, failureReason, 2, 0, 0,
				Duration.ofSeconds(1), Duration.ofMillis(900), Duration.ofMillis(50), 3, 3, 3, 0, Map.of());
	}

	private static NonpayCollectResult nonpayResult(SnapshotStatus status, String reason) {
		return new NonpayCollectResult(2L, status, reason, status == SnapshotStatus.TRIAL, 2, 6, 0, 0,
				Duration.ofSeconds(1), Duration.ofMillis(900), Duration.ofMillis(50), 2, 12, 5, 4, 1, 0, 1L, 0,
				Map.of());
	}

}
