package dev.chhun.hospitalcompare.detail.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.Lunch;
import dev.chhun.hospitalcompare.detail.dto.Note;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import dev.chhun.hospitalcompare.detail.dto.OpenStatus;
import dev.chhun.hospitalcompare.detail.dto.WeeklyHours;
import dev.chhun.hospitalcompare.detail.entity.NoteKind;
import dev.chhun.hospitalcompare.detail.repository.DetailUpsertRepository;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 고정 시계 2026-10-07(수) 10:00. 2026-10-09(한글날)은 application.yml의 공휴일 목록에 있다. */
@ApiIntegrationTest
class OpenStatusServiceTest {

	@Autowired
	OpenStatusService service;

	@Autowired
	DetailUpsertRepository upsertRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	long activeId;

	@BeforeEach
	void setUp() {
		for (String table : List.of("hospital_detail", "hospital_subject", "snapshot")) {
			jdbcTemplate.update("delete from " + table);
		}
		activeId = insertSnapshot("ACTIVE");
		long stageId = insertSnapshot("STAGE");
		upsertRepository.upsertDetails(activeId, List.of(weekdays("Y-OPEN", Note.EMPTY),
				weekdays("Y-HOLI", new Note(NoteKind.CLOSED, "전부 휴진"))));
		upsertRepository.upsertDetails(stageId, List.of(weekdays("Y-STAGE", Note.EMPTY)));
	}

	@Test
	void 지금은_고정_시계의_한국_시각이다() {
		assertThat(service.now()).isEqualTo(LocalDateTime.of(2026, 10, 7, 10, 0));
	}

	@Test
	void 상세_ACTIVE의_세부정보로_판정하고_없는_기관은_정보_없음이다() {
		Map<String, OpenStatus> statuses = service.statuses(List.of("Y-OPEN", "Y-NONE"), service.now());

		assertThat(statuses).containsEntry("Y-OPEN", OpenStatus.OPEN).containsEntry("Y-NONE", OpenStatus.NO_INFO);
	}

	@Test
	void STAGE_스냅샷의_세부정보는_읽지_않는다() {
		assertThat(service.statuses(List.of("Y-STAGE"), service.now())).containsEntry("Y-STAGE", OpenStatus.NO_INFO);
	}

	@Test
	void 설정한_공휴일이면_공휴일_안내로_판정한다() {
		LocalDateTime hangulDay = LocalDateTime.of(2026, 10, 9, 10, 0);

		assertThat(service.statuses(List.of("Y-HOLI", "Y-OPEN"), hangulDay))
				.containsEntry("Y-HOLI", OpenStatus.closed("공휴일 휴진"))
				.containsEntry("Y-OPEN", OpenStatus.unknown("공휴일 진료 여부 확인 필요"));
	}

	@Test
	void 상세_ACTIVE가_없으면_모두_정보_없음이다() {
		jdbcTemplate.update("delete from snapshot where id = ?", activeId);

		assertThat(service.statuses(List.of("Y-OPEN"), service.now())).containsEntry("Y-OPEN", OpenStatus.NO_INFO);
		assertThat(service.statuses(List.of(), service.now())).isEmpty();
	}

	private long insertSnapshot(String status) {
		jdbcTemplate.update("""
				insert into snapshot (source, status, base_date, record_count)
				values ('HOSPITAL_DETAIL', ?, '2026-10-06', 0)
				""", status);
		return jdbcTemplate.queryForObject("select max(id) from snapshot", Long.class);
	}

	/** 월~금 09:00~18:00, 점심 없음 */
	private static HospitalDetailRecord weekdays(String ykiho, Note holidayNote) {
		Map<DayOfWeek, OpenRange> days = new EnumMap<>(DayOfWeek.class);
		for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
				DayOfWeek.FRIDAY)) {
			days.put(day, new OpenRange(540, 1080));
		}
		return new HospitalDetailRecord(ykiho, new WeeklyHours(days, Set.of()), Lunch.none(null), Lunch.none(null),
				null, null, Note.EMPTY, holidayNote, null, null, null, null, null);
	}

}
