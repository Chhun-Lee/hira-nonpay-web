package dev.chhun.hospitalcompare.nonpay.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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
 * 3-3 스키마(병원별 인덱스 없음)로 만든 기존 nonpay_price에 새 코드가 뜨면 (snapshot_id, ykiho) 인덱스가 생기는지 본다.
 * 다른 테스트는 모두 빈 DB에서 스키마를 새로 만들어 스키마 진화를 확인할 수 없다(결정 문서 007의 교훈).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NonpayPriceIndexEvolutionTest {

	private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	static {
		MYSQL.start();
		try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(),
				MYSQL.getPassword()); Statement statement = connection.createStatement()) {
			// 3-3 마지막 커밋(3237c00) 로컬 DB의 nonpay_price 열 그대로(SHOW COLUMNS, 2026-10-06)
			statement.execute("""
					create table nonpay_price (
					    id bigint not null auto_increment,
					    adt_fr_dd date,
					    cl_cd varchar(10),
					    max_price bigint not null,
					    min_price bigint not null,
					    npay_cd varchar(50) not null,
					    sggu_cd varchar(10),
					    sido_cd varchar(10),
					    snapshot_id bigint not null,
					    ykiho varchar(128) not null,
					    primary key (id),
					    constraint uk_nonpay_price_snapshot_code_ykiho unique (snapshot_id, npay_cd, ykiho)
					) engine=InnoDB""");
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

	@Test
	void 기존_가격_테이블에_병원별_인덱스가_생긴다() {
		List<String> columns = jdbcTemplate.queryForList("""
				select column_name from information_schema.statistics
				where table_schema = database() and table_name = 'nonpay_price'
				  and index_name = 'idx_nonpay_price_snapshot_ykiho'
				order by seq_in_index
				""", String.class);

		assertThat(columns).containsExactly("snapshot_id", "ykiho");
	}

}
