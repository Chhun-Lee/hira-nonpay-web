package dev.chhun.hospitalcompare.collector.runner;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.chhun.hospitalcompare.collector.dto.CollectResult;
import dev.chhun.hospitalcompare.collector.service.HospitalCollector;
import dev.chhun.hospitalcompare.hospital.entity.SnapshotStatus;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class CollectorRunnerTest {

	private final HospitalCollector collector = mock(HospitalCollector.class);
	private final CollectorRunner runner = new CollectorRunner(collector);

	@Test
	void 인자가_없으면_전국을_수집한다() {
		when(collector.collect(any())).thenReturn(result(SnapshotStatus.ACTIVE, null));

		runner.run(new DefaultApplicationArguments());

		verify(collector).collect(null);
	}

	@Test
	void sgguCd를_주면_그_시군구를_수집한다() {
		when(collector.collect(any())).thenReturn(result(SnapshotStatus.ACTIVE, null));

		runner.run(new DefaultApplicationArguments("--sgguCd=110001"));

		verify(collector).collect("110001");
	}

	@Test
	void 수집이_실패하면_예외로_끝내_종료_코드를_남긴다() {
		when(collector.collect(any())).thenReturn(result(SnapshotStatus.FAILED, "건수 급감"));

		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("건수 급감");
	}

	@Test
	void 시군구를_둘_이상_주면_거절한다() {
		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--sgguCd=110001", "--sgguCd=110002")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static CollectResult result(SnapshotStatus status, String failureReason) {
		return new CollectResult("전국", 1L, status, failureReason, 2, 0, 0,
				Duration.ofSeconds(1), Duration.ofMillis(900), Duration.ofMillis(50), 3, 3, 3, 0, Map.of());
	}

}
