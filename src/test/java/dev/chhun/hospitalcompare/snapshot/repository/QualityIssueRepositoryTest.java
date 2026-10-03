package dev.chhun.hospitalcompare.snapshot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import dev.chhun.hospitalcompare.snapshot.dto.QualityIssue;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({TestcontainersConfiguration.class, QualityIssueRepository.class})
class QualityIssueRepositoryTest {

	@Autowired
	QualityIssueRepository repository;

	@Test
	void 스냅샷별로_유형마다_센다() {
		repository.saveAll(1L, List.of(
				new QualityIssue("Y1", QualityIssueType.MISSING_COORDINATES, "XPos=null, YPos=null"),
				new QualityIssue("Y2", QualityIssueType.MISSING_COORDINATES, "XPos=null, YPos=null"),
				new QualityIssue(null, QualityIssueType.MISSING_REQUIRED, "ykiho=null, yadmNm=병원, clCd=31")));
		repository.saveAll(2L, List.of(new QualityIssue("Y3", QualityIssueType.INVALID_DATE, "estbDd=00000000")));

		assertThat(repository.countBySnapshot(1L))
				.containsEntry(QualityIssueType.MISSING_COORDINATES, 2)
				.containsEntry(QualityIssueType.MISSING_REQUIRED, 1)
				.hasSize(2);
	}

	@Test
	void 원본_값이_길면_1000자로_자른다() {
		repository.saveAll(1L, List.of(new QualityIssue("Y1", QualityIssueType.INVALID_DATE, "x".repeat(1500))));

		assertThat(repository.countBySnapshot(1L)).containsEntry(QualityIssueType.INVALID_DATE, 1);
	}

}
