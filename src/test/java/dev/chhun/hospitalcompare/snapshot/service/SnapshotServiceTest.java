package dev.chhun.hospitalcompare.snapshot.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import dev.chhun.hospitalcompare.hospital.repository.HospitalUpsertRepository;
import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.snapshot.repository.SnapshotRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import({TestcontainersConfiguration.class, SnapshotService.class, HospitalUpsertRepository.class,
		SnapshotServiceTest.FakeNonpayCleanerConfig.class})
class SnapshotServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 3);
	private static final SnapshotSource HOSPITAL = SnapshotSource.HOSPITAL_LIST;
	private static final SnapshotSource NONPAY = SnapshotSource.NONPAY;

	@Autowired
	SnapshotService snapshotService;

	@Autowired
	SnapshotRepository snapshotRepository;

	@Autowired
	TestEntityManager em;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	RecordingCleaner nonpayCleaner;

	@BeforeEach
	void setUp() {
		nonpayCleaner.deleted.clear();
	}

	@Test
	void 새_STAGE를_만들고_같은_출처의_남은_STAGE만_FAILED로_정리한다() {
		long hospitalLeftover = save(HOSPITAL, SnapshotStatus.STAGE);
		long nonpayStage = save(NONPAY, SnapshotStatus.STAGE);

		Snapshot stage = snapshotService.startStage(HOSPITAL, TODAY);
		em.flush();
		em.clear();

		assertThat(statusOf(stage.getId())).isEqualTo(SnapshotStatus.STAGE);
		assertThat(snapshotRepository.findById(stage.getId()).orElseThrow().getSource()).isEqualTo(HOSPITAL);
		Snapshot failed = snapshotRepository.findById(hospitalLeftover).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(failed.getFailureReason()).isEqualTo(SnapshotService.INTERRUPTED);
		assertThat(statusOf(nonpayStage)).isEqualTo(SnapshotStatus.STAGE);
	}

	@Test
	void 전환하면_같은_출처의_ACTIVE만_RETIRED가_된다() {
		long hospitalActive = save(HOSPITAL, SnapshotStatus.ACTIVE);
		long nonpayActive = save(NONPAY, SnapshotStatus.ACTIVE);
		long stage = save(HOSPITAL, SnapshotStatus.STAGE);

		snapshotService.activate(stage, 42);
		em.flush();
		em.clear();

		assertThat(statusOf(hospitalActive)).isEqualTo(SnapshotStatus.RETIRED);
		assertThat(statusOf(nonpayActive)).isEqualTo(SnapshotStatus.ACTIVE);
		assertThat(statusOf(stage)).isEqualTo(SnapshotStatus.ACTIVE);
		assertThat(snapshotService.activeRecordCount(HOSPITAL)).contains(42);
		assertThat(snapshotService.activeSnapshotId(HOSPITAL)).contains(stage);
		assertThat(snapshotService.activeSnapshotId(NONPAY)).contains(nonpayActive);
	}

	@Test
	void 실패하면_사유를_남긴다() {
		long stage = save(HOSPITAL, SnapshotStatus.STAGE);

		snapshotService.fail(stage, "건수 급감");
		em.flush();
		em.clear();

		Snapshot failed = snapshotRepository.findById(stage).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(failed.getFailureReason()).isEqualTo("건수 급감");
	}

	@Test
	void 시험_실행은_TRIAL로_끝나고_사유를_남긴다() {
		long stage = save(NONPAY, SnapshotStatus.STAGE);

		snapshotService.endTrial(stage, "시험 실행(일부 항목 2개)");
		em.flush();
		em.clear();

		Snapshot trial = snapshotRepository.findById(stage).orElseThrow();
		assertThat(trial.getStatus()).isEqualTo(SnapshotStatus.TRIAL);
		assertThat(trial.getFailureReason()).isEqualTo("시험 실행(일부 항목 2개)");
		assertThat(snapshotService.activeRecordCount(NONPAY)).isEmpty();
	}

	@Test
	void 정리하면_같은_출처의_ACTIVE와_최신_RETIRED만_행을_남긴다() {
		long oldRetired = save(HOSPITAL, SnapshotStatus.RETIRED);
		long newRetired = save(HOSPITAL, SnapshotStatus.RETIRED);
		long failed = save(HOSPITAL, SnapshotStatus.FAILED);
		long trial = save(HOSPITAL, SnapshotStatus.TRIAL);
		long active = save(HOSPITAL, SnapshotStatus.ACTIVE);
		long stage = save(HOSPITAL, SnapshotStatus.STAGE);
		for (long snapshotId : new long[] {oldRetired, newRetired, failed, trial, active, stage}) {
			jdbcTemplate.update("insert into hospital (snapshot_id, ykiho, name) values (?, 'Y', '병원')", snapshotId);
		}

		int deleted = snapshotService.cleanup(HOSPITAL);

		assertThat(deleted).isEqualTo(3);
		assertThat(rowsOf(oldRetired)).isZero();
		assertThat(rowsOf(failed)).isZero();
		assertThat(rowsOf(trial)).isZero();
		assertThat(rowsOf(newRetired)).isEqualTo(1);
		assertThat(rowsOf(active)).isEqualTo(1);
		assertThat(rowsOf(stage)).isEqualTo(1);
		assertThat(nonpayCleaner.deleted).isEmpty();
	}

	@Test
	void 정리는_그_출처의_정리기만_부르고_다른_출처의_행은_건드리지_않는다() {
		long hospitalFailed = save(HOSPITAL, SnapshotStatus.FAILED);
		jdbcTemplate.update("insert into hospital (snapshot_id, ykiho, name) values (?, 'Y', '병원')", hospitalFailed);
		long nonpayOld = save(NONPAY, SnapshotStatus.RETIRED);
		long nonpayNew = save(NONPAY, SnapshotStatus.RETIRED);
		long nonpayTrial = save(NONPAY, SnapshotStatus.TRIAL);

		snapshotService.cleanup(NONPAY);

		assertThat(nonpayCleaner.deleted).containsExactlyInAnyOrder(nonpayOld, nonpayTrial);
		assertThat(nonpayCleaner.deleted).doesNotContain(nonpayNew);
		assertThat(rowsOf(hospitalFailed)).isEqualTo(1);
	}

	private long save(SnapshotSource source, SnapshotStatus status) {
		return snapshotRepository.saveAndFlush(new Snapshot(source, TODAY, status)).getId();
	}

	private SnapshotStatus statusOf(long snapshotId) {
		return snapshotRepository.findById(snapshotId).orElseThrow().getStatus();
	}

	private int rowsOf(long snapshotId) {
		return jdbcTemplate.queryForObject("select count(*) from hospital where snapshot_id = ?", Integer.class,
				snapshotId);
	}

	@TestConfiguration
	static class FakeNonpayCleanerConfig {

		@Bean
		RecordingCleaner recordingCleaner() {
			return new RecordingCleaner();
		}

	}

	/** 비급여 정리기 대역. 지우라고 받은 스냅샷 id만 기록한다. */
	static class RecordingCleaner implements SnapshotRowCleaner {

		final List<Long> deleted = new CopyOnWriteArrayList<>();

		@Override
		public SnapshotSource source() {
			return SnapshotSource.NONPAY;
		}

		@Override
		public int deleteRows(Collection<Long> snapshotIds) {
			deleted.addAll(snapshotIds);
			return snapshotIds.size();
		}

	}

}
