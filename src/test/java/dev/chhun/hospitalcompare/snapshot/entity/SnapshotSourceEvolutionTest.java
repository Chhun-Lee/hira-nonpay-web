package dev.chhun.hospitalcompare.snapshot.entity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.snapshot.repository.SnapshotRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 3-1 스키마(source 열 없음)로 만든 기존 DB에서 새 코드가 뜨면, 기존 스냅샷이 병원 목록 출처로 채워지는지 본다.
 * 다른 테스트는 모두 빈 DB에서 스키마를 새로 만들어 스키마 진화를 확인할 수 없다(결정 문서 007의 교훈).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SnapshotSourceEvolutionTest {

	private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	static {
		MYSQL.start();
		try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(),
				MYSQL.getPassword()); Statement statement = connection.createStatement()) {
			// 3-1 마지막 커밋(30b87ee)에서 Hibernate가 만든 snapshot 테이블 그대로
			statement.execute("""
					create table snapshot (
					    id bigint not null auto_increment,
					    base_date date not null,
					    failure_reason varchar(500),
					    record_count integer not null,
					    status varchar(10) not null,
					    primary key (id)
					) engine=InnoDB""");
			statement.execute(
					"insert into snapshot (base_date, status, record_count) values ('2026-10-03', 'ACTIVE', 79867)");
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
	}

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	SnapshotRepository snapshotRepository;

	@Test
	void 기존_스냅샷은_병원_목록_출처가_된다() {
		assertThat(jdbcTemplate.queryForObject("select source from snapshot where id = 1", String.class))
				.isEqualTo("HOSPITAL_LIST");
		assertThat(snapshotRepository.findBySourceAndStatus(SnapshotSource.HOSPITAL_LIST, SnapshotStatus.ACTIVE))
				.get().extracting(Snapshot::getRecordCount).isEqualTo(79867);
	}

}
