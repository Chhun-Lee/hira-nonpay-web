package dev.chhun.hospitalcompare.detail.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.HospitalSubjectRecord;
import dev.chhun.hospitalcompare.detail.dto.Lunch;
import dev.chhun.hospitalcompare.detail.dto.Note;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import dev.chhun.hospitalcompare.detail.dto.WeeklyHours;
import dev.chhun.hospitalcompare.detail.entity.NoteKind;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@ApiIntegrationTest
class DetailRepositoryTest {

	@Autowired
	DetailUpsertRepository upsertRepository;

	@Autowired
	DetailQueryRepository queryRepository;

	@Autowired
	DetailRowCleaner rowCleaner;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("delete from hospital_detail");
		jdbcTemplate.update("delete from hospital_subject");
	}

	@Test
	void 세부정보는_해석한_값과_원문이_그대로_돌아온다() {
		HospitalDetailRecord saved = kimEye("Y-KIM");

		upsertRepository.upsertDetails(1L, List.of(saved));

		assertThat(queryRepository.findDetail(1L, "Y-KIM")).contains(saved);
		assertThat(queryRepository.findDetail(2L, "Y-KIM")).isEmpty();
	}

	@Test
	void 같은_기관을_다시_넣으면_한_행이고_값은_새것이다() {
		upsertRepository.upsertDetails(1L, List.of(kimEye("Y-KIM")));
		HospitalDetailRecord changed = new HospitalDetailRecord("Y-KIM", WeeklyHours.NONE, null, null, null, null,
				null, null, true, true, 5, true, "바뀜");
		upsertRepository.upsertDetails(1L, List.of(changed));

		assertThat(upsertRepository.countDetails(1L)).isEqualTo(1);
		assertThat(queryRepository.findDetail(1L, "Y-KIM")).contains(changed);
	}

	@Test
	void 여러_기관을_한_번에_찾는다() {
		upsertRepository.upsertDetails(1L, List.of(kimEye("Y-A"), kimEye("Y-B")));

		Map<String, HospitalDetailRecord> found = queryRepository.findDetails(1L, List.of("Y-A", "Y-B", "Y-NONE"));

		assertThat(found).containsOnlyKeys("Y-A", "Y-B");
		assertThat(queryRepository.findDetails(1L, List.of())).isEmpty();
	}

	@Test
	void 진료과목은_과목_코드_순이고_다시_넣으면_새_값이다() {
		upsertRepository.upsertSubjects(1L, List.of(
				new HospitalSubjectRecord("Y-KIM", "12", "안과", 40),
				new HospitalSubjectRecord("Y-KIM", "01", "내과", null)));
		upsertRepository.upsertSubjects(1L, List.of(new HospitalSubjectRecord("Y-KIM", "12", "안과", 47)));

		assertThat(queryRepository.findSubjects(1L, "Y-KIM")).containsExactly(
				new HospitalSubjectRecord("Y-KIM", "01", "내과", null),
				new HospitalSubjectRecord("Y-KIM", "12", "안과", 47));
	}

	@Test
	void 정리기는_지정한_스냅샷의_두_테이블_행만_지운다() {
		upsertRepository.upsertDetails(1L, List.of(kimEye("Y-KIM")));
		upsertRepository.upsertDetails(2L, List.of(kimEye("Y-KIM")));
		upsertRepository.upsertSubjects(1L, List.of(new HospitalSubjectRecord("Y-KIM", "12", "안과", 47)));
		upsertRepository.upsertSubjects(2L, List.of(new HospitalSubjectRecord("Y-KIM", "12", "안과", 47)));

		int deleted = rowCleaner.deleteRows(List.of(1L));

		assertThat(rowCleaner.source()).isEqualTo(SnapshotSource.HOSPITAL_DETAIL);
		assertThat(deleted).isEqualTo(2);
		assertThat(upsertRepository.countDetails(1L)).isZero();
		assertThat(upsertRepository.countDetails(2L)).isEqualTo(1);
		assertThat(queryRepository.findSubjects(2L, "Y-KIM")).hasSize(1);
	}

	/** 김안과병원(2026-10-05 실제 응답)을 해석한 값. 수요일은 시각을 읽지 못한 것으로, 일요일은 24:00 종료로 바꿨다. */
	private static HospitalDetailRecord kimEye(String ykiho) {
		return new HospitalDetailRecord(ykiho,
				new WeeklyHours(Map.of(
						DayOfWeek.MONDAY, new OpenRange(510, 1020),
						DayOfWeek.SATURDAY, new OpenRange(510, 750),
						DayOfWeek.SUNDAY, new OpenRange(510, 1440)),
						Set.of(DayOfWeek.WEDNESDAY)),
				Lunch.range(750, 810, "12시30분~1시30분"),
				Lunch.unknown("점심 : 13시00분 ~ 14시00분, 저녁 : 18시00분 ~ 19시00분"),
				"08시00분~17시00분", "08시00분~12시00분",
				Note.EMPTY, new Note(NoteKind.CLOSED, "전부 휴진"),
				false, null, 100, false, "외래진료차량-주차권 확인시 무료");
	}

}
