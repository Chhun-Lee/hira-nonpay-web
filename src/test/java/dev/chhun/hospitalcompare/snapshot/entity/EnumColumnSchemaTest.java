package dev.chhun.hospitalcompare.snapshot.entity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * enum 컬럼에 값 목록 CHECK 제약이 없는지 확인한다. Hibernate가 CHECK를 만들면 ddl-auto=update가 나중에 고치지 않아,
 * enum 값을 추가한 뒤 기존 DB에서 INSERT가 실패한다. 새 enum 컬럼을 추가할 때도 이 테스트가 지킨다.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class EnumColumnSchemaTest {

	@Autowired
	TestEntityManager em;

	@Test
	void snapshot과_data_quality_issue에는_CHECK_제약이_없다() {
		List<?> checks = em.getEntityManager().createNativeQuery("""
				select table_name from information_schema.table_constraints
				where table_schema = database() and constraint_type = 'CHECK'
				  and table_name in ('snapshot', 'data_quality_issue', 'nonpay_stat')
				""").getResultList();

		assertThat(checks).isEmpty();
	}

	@Test
	void nonpay_stat의_dimension은_CHECK_없는_VARCHAR다() {
		List<?> types = em.getEntityManager().createNativeQuery("""
				select data_type from information_schema.columns
				where table_schema = database() and table_name = 'nonpay_stat' and column_name = 'dimension'
				""").getResultList();

		assertThat(types).hasSize(1);
		assertThat(types.get(0).toString()).isEqualToIgnoringCase("varchar");
	}

	@Test
	void enum은_이름_문자열로_저장된다() {
		Snapshot snapshot = em.persistAndFlush(new Snapshot(SnapshotSource.HOSPITAL_LIST, LocalDate.of(2026, 10, 3), SnapshotStatus.STAGE));

		Object stored = em.getEntityManager()
				.createNativeQuery("select status from snapshot where id = ?")
				.setParameter(1, snapshot.getId())
				.getSingleResult();
		assertThat(stored).isEqualTo("STAGE");
	}

	@Test
	void enum에_없는_값도_DDL_변경_없이_들어간다() {
		em.getEntityManager().createNativeQuery(
				"insert into snapshot (base_date, status, record_count) values (curdate(), 'NEW_STATUS', 0)")
				.executeUpdate();
		em.getEntityManager().createNativeQuery(
				"insert into data_quality_issue (snapshot_id, issue_type) values (1, 'NEW_ISSUE_TYPE')")
				.executeUpdate();

		Number count = (Number) em.getEntityManager()
				.createNativeQuery("select count(*) from snapshot where status = 'NEW_STATUS'")
				.getSingleResult();
		assertThat(count.intValue()).isEqualTo(1);
	}

}
