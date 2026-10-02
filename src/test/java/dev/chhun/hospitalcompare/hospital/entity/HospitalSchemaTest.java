package dev.chhun.hospitalcompare.hospital.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * ddl-auto=update가 만든 hospital 테이블이 수집 upsert가 기대는 제약을 갖췄는지 확인한다.
 * 적재는 collector의 JDBC upsert가 하므로 여기서도 SQL로 넣는다.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class HospitalSchemaTest {

	@Autowired
	TestEntityManager em;

	@Test
	void 같은_스냅샷에_같은_ykiho는_한_번만_들어간다() {
		insert("YKIHO-1", 1L);

		assertThatThrownBy(() -> insert("YKIHO-1", 1L))
				.hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class)
				.rootCause()
				.hasMessageContaining("uk_hospital_ykiho_snapshot");
	}

	@Test
	void 다른_스냅샷에는_같은_ykiho가_들어갈_수_있다() {
		insert("YKIHO-1", 1L);
		insert("YKIHO-1", 2L);

		Number count = (Number) em.getEntityManager()
				.createNativeQuery("select count(*) from hospital where ykiho = 'YKIHO-1'")
				.getSingleResult();
		assertThat(count.intValue()).isEqualTo(2);
	}

	@Test
	void 좌표는_DOUBLE_컬럼이다() {
		List<?> types = em.getEntityManager().createNativeQuery("""
				select data_type from information_schema.columns
				where table_schema = database() and table_name = 'hospital'
				  and column_name in ('latitude', 'longitude')
				""").getResultList();

		assertThat(types).hasSize(2).allMatch("double"::equals);
	}

	private void insert(String ykiho, long snapshotId) {
		em.getEntityManager()
				.createNativeQuery("insert into hospital (ykiho, snapshot_id, name) values (?, ?, '테스트병원')")
				.setParameter(1, ykiho)
				.setParameter(2, snapshotId)
				.executeUpdate();
	}

}
