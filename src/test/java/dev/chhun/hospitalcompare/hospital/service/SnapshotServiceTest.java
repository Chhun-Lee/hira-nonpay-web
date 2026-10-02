package dev.chhun.hospitalcompare.hospital.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import dev.chhun.hospitalcompare.hospital.entity.Snapshot;
import dev.chhun.hospitalcompare.hospital.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.hospital.repository.HospitalUpsertRepository;
import dev.chhun.hospitalcompare.hospital.repository.SnapshotRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import({TestcontainersConfiguration.class, SnapshotService.class, HospitalUpsertRepository.class})
class SnapshotServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

	@Autowired
	SnapshotService snapshotService;

	@Autowired
	SnapshotRepository snapshotRepository;

	@Autowired
	TestEntityManager em;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 새_STAGE를_만들고_남아_있던_STAGE는_FAILED로_정리한다() {
		Snapshot leftover = snapshotRepository.save(new Snapshot(TODAY.minusDays(1), SnapshotStatus.STAGE));

		Snapshot stage = snapshotService.startStage(TODAY);
		em.flush();
		em.clear();

		assertThat(snapshotRepository.findById(stage.getId()).orElseThrow().getStatus()).isEqualTo(SnapshotStatus.STAGE);
		Snapshot failed = snapshotRepository.findById(leftover.getId()).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(failed.getFailureReason()).isEqualTo(SnapshotService.INTERRUPTED);
	}

	@Test
	void 전환하면_기존_ACTIVE는_RETIRED가_되고_STAGE가_ACTIVE가_된다() {
		Snapshot active = snapshotRepository.save(new Snapshot(TODAY.minusDays(7), SnapshotStatus.ACTIVE));
		Snapshot stage = snapshotRepository.save(new Snapshot(TODAY, SnapshotStatus.STAGE));

		snapshotService.activate(stage.getId(), 42);
		em.flush();
		em.clear();

		assertThat(snapshotRepository.findById(active.getId()).orElseThrow().getStatus()).isEqualTo(SnapshotStatus.RETIRED);
		Snapshot activated = snapshotRepository.findById(stage.getId()).orElseThrow();
		assertThat(activated.getStatus()).isEqualTo(SnapshotStatus.ACTIVE);
		assertThat(activated.getRecordCount()).isEqualTo(42);
		assertThat(snapshotService.activeRecordCount()).contains(42);
	}

	@Test
	void 실패하면_사유를_남긴다() {
		Snapshot stage = snapshotRepository.save(new Snapshot(TODAY, SnapshotStatus.STAGE));

		snapshotService.fail(stage.getId(), "건수 급감");
		em.flush();
		em.clear();

		Snapshot failed = snapshotRepository.findById(stage.getId()).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(SnapshotStatus.FAILED);
		assertThat(failed.getFailureReason()).isEqualTo("건수 급감");
	}

	@Test
	void 정리하면_ACTIVE와_최신_RETIRED만_병원_행을_남긴다() {
		long oldRetired = save(SnapshotStatus.RETIRED);
		long newRetired = save(SnapshotStatus.RETIRED);
		long failed = save(SnapshotStatus.FAILED);
		long active = save(SnapshotStatus.ACTIVE);
		long stage = save(SnapshotStatus.STAGE);
		for (long snapshotId : new long[] {oldRetired, newRetired, failed, active, stage}) {
			jdbcTemplate.update("insert into hospital (snapshot_id, ykiho, name) values (?, 'Y', '병원')", snapshotId);
		}

		int deleted = snapshotService.cleanup();

		assertThat(deleted).isEqualTo(2);
		assertThat(rowsOf(oldRetired)).isZero();
		assertThat(rowsOf(failed)).isZero();
		assertThat(rowsOf(newRetired)).isEqualTo(1);
		assertThat(rowsOf(active)).isEqualTo(1);
		assertThat(rowsOf(stage)).isEqualTo(1);
		assertThat(snapshotRepository.count()).isEqualTo(5);
	}

	private long save(SnapshotStatus status) {
		return snapshotRepository.saveAndFlush(new Snapshot(TODAY, status)).getId();
	}

	private int rowsOf(long snapshotId) {
		return jdbcTemplate.queryForObject("select count(*) from hospital where snapshot_id = ?", Integer.class, snapshotId);
	}

}
