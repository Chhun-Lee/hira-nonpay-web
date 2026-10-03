package dev.chhun.hospitalcompare.nonpay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import dev.chhun.hospitalcompare.hospital.repository.HospitalUpsertRepository;
import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.snapshot.repository.SnapshotRepository;
import dev.chhun.hospitalcompare.snapshot.service.SnapshotService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 나눠 지우기를 확인하려고 한 번에 2행씩 지우게 한다. */
@DataJpaTest(properties = "collector.nonpay.delete-chunk-size=2")
@Import({TestcontainersConfiguration.class, NonpayRowCleaner.class, HospitalUpsertRepository.class,
		SnapshotService.class})
class NonpayRowCleanerTest {

	@Autowired
	NonpayRowCleaner cleaner;

	@Autowired
	SnapshotService snapshotService;

	@Autowired
	SnapshotRepository snapshotRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 가격_행을_나눠서_끝까지_지우고_항목과_통계도_지운다() {
		insertNonpayRows(7L, 5);
		insertNonpayRows(8L, 1);

		int deleted = cleaner.deleteRows(List.of(7L));

		assertThat(deleted).isEqualTo(7);
		assertThat(count("nonpay_price", 7L)).isZero();
		assertThat(count("nonpay_item", 7L)).isZero();
		assertThat(count("nonpay_stat", 7L)).isZero();
		assertThat(count("nonpay_price", 8L)).isEqualTo(1);
	}

	@Test
	void 비급여_정리는_병원_행을_지우지_않는다() {
		long hospitalFailed = save(SnapshotSource.HOSPITAL_LIST, SnapshotStatus.FAILED);
		jdbcTemplate.update("insert into hospital (snapshot_id, ykiho, name) values (?, 'Y', '병원')", hospitalFailed);
		long nonpayFailed = save(SnapshotSource.NONPAY, SnapshotStatus.FAILED);
		insertNonpayRows(nonpayFailed, 3);

		snapshotService.cleanup(SnapshotSource.NONPAY);

		assertThat(count("nonpay_price", nonpayFailed)).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from hospital where snapshot_id = ?", Integer.class,
				hospitalFailed)).isEqualTo(1);
	}

	private void insertNonpayRows(long snapshotId, int prices) {
		for (int i = 0; i < prices; i++) {
			jdbcTemplate.update("""
					insert into nonpay_price (snapshot_id, npay_cd, ykiho, min_price, max_price)
					values (?, 'ABZ010001', ?, 1, 1)
					""", snapshotId, "Y-" + i);
		}
		jdbcTemplate.update("insert into nonpay_item (snapshot_id, npay_cd) values (?, 'ABZ010001')", snapshotId);
		jdbcTemplate.update(
				"insert into nonpay_stat (snapshot_id, npay_cd, dimension, dim_key) values (?, 'ABZ010001', 'SIDO', 'Sl')",
				snapshotId);
	}

	private long save(SnapshotSource source, SnapshotStatus status) {
		return snapshotRepository.saveAndFlush(new Snapshot(source, LocalDate.of(2026, 10, 3), status)).getId();
	}

	private int count(String table, long snapshotId) {
		return jdbcTemplate.queryForObject("select count(*) from " + table + " where snapshot_id = ?", Integer.class,
				snapshotId);
	}

}
