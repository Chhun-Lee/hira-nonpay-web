package dev.chhun.hospitalcompare.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import({TestcontainersConfiguration.class, HospitalUpsertRepository.class})
class HospitalUpsertRepositoryTest {

	@Autowired
	HospitalUpsertRepository repository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 같은_스냅샷의_같은_ykiho는_새_값으로_갱신한다() {
		repository.upsertAll(1L, List.of(record("YKIHO-1", "옛이름", 1)));
		repository.upsertAll(1L, List.of(record("YKIHO-1", "새이름", 5)));

		assertThat(repository.countBySnapshot(1L)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForMap("select name, doctor_count from hospital where ykiho = 'YKIHO-1'"))
				.containsEntry("name", "새이름")
				.containsEntry("doctor_count", 5);
	}

	@Test
	void 스냅샷이_다르면_같은_ykiho도_따로_쌓는다() {
		repository.upsertAll(1L, List.of(record("YKIHO-1", "병원", 1)));
		repository.upsertAll(2L, List.of(record("YKIHO-1", "병원", 1)));

		assertThat(repository.countBySnapshot(1L)).isEqualTo(1);
		assertThat(repository.countBySnapshot(2L)).isEqualTo(1);
	}

	@Test
	void 빈_목록은_아무것도_하지_않는다() {
		repository.upsertAll(1L, List.of());

		assertThat(repository.countBySnapshot(1L)).isZero();
	}

	private static HospitalRecord record(String ykiho, String name, int doctorCount) {
		return new HospitalRecord(ykiho, name, "31", "의원", "110000", "서울", "110001", "강남구", "역삼동",
				"서울특별시 강남구 가상로 1", "02-0000-0000", LocalDate.of(2020, 1, 1), 37.5, 127.0, doctorCount);
	}

}
