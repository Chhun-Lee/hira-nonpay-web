package dev.chhun.hospitalcompare.detail.repository;

import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.HospitalSubjectRecord;
import dev.chhun.hospitalcompare.detail.dto.Lunch;
import dev.chhun.hospitalcompare.detail.dto.Note;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import dev.chhun.hospitalcompare.detail.dto.WeeklyHours;
import dev.chhun.hospitalcompare.detail.entity.LunchKind;
import dev.chhun.hospitalcompare.detail.entity.NoteKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 세부정보·진료과목 조회. 기관은 유니크 키 (snapshot_id, ykiho)로 찾는다. */
@Repository
public class DetailQueryRepository {

	/** IN 목록 한 번에 넣는 ykiho 수 */
	private static final int CHUNK = 1000;

	private static final String SELECT_DETAIL = "SELECT ykiho, " + String.join(", ", DetailColumns.DATA)
			+ " FROM hospital_detail WHERE snapshot_id = :snapshotId";

	private final JdbcClient jdbcClient;

	public DetailQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<HospitalDetailRecord> findDetail(long snapshotId, String ykiho) {
		return jdbcClient.sql(SELECT_DETAIL + " AND ykiho = :ykiho")
				.param("snapshotId", snapshotId)
				.param("ykiho", ykiho)
				.query(DetailQueryRepository::detail)
				.optional();
	}

	/** 찾은 기관만 담는다(ykiho → 세부정보) */
	public Map<String, HospitalDetailRecord> findDetails(long snapshotId, Collection<String> ykihos) {
		Map<String, HospitalDetailRecord> found = new HashMap<>();
		List<String> all = List.copyOf(new LinkedHashSet<>(ykihos));
		for (int from = 0; from < all.size(); from += CHUNK) {
			jdbcClient.sql(SELECT_DETAIL + " AND ykiho IN (:ykihos)")
					.param("snapshotId", snapshotId)
					.param("ykihos", all.subList(from, Math.min(all.size(), from + CHUNK)))
					.query(DetailQueryRepository::detail)
					.list()
					.forEach(record -> found.put(record.ykiho(), record));
		}
		return found;
	}

	public List<HospitalSubjectRecord> findSubjects(long snapshotId, String ykiho) {
		return jdbcClient.sql("""
						SELECT ykiho, subject_code, subject_name, specialist_count
						FROM hospital_subject
						WHERE snapshot_id = :snapshotId AND ykiho = :ykiho
						ORDER BY subject_code
						""")
				.param("snapshotId", snapshotId)
				.param("ykiho", ykiho)
				.query((rs, rowNum) -> new HospitalSubjectRecord(rs.getString("ykiho"), rs.getString("subject_code"),
						rs.getString("subject_name"), rs.getObject("specialist_count", Integer.class)))
				.list();
	}

	private static HospitalDetailRecord detail(ResultSet rs, int rowNum) throws SQLException {
		Map<DayOfWeek, OpenRange> days = new EnumMap<>(DayOfWeek.class);
		for (DayOfWeek day : DayOfWeek.values()) {
			String prefix = DetailColumns.prefix(day);
			Integer open = rs.getObject(prefix + "_open", Integer.class);
			Integer close = rs.getObject(prefix + "_close", Integer.class);
			if (open != null && close != null) {
				days.put(day, new OpenRange(open, close));
			}
		}
		return new HospitalDetailRecord(
				rs.getString("ykiho"),
				new WeeklyHours(days, DetailColumns.parseDays(rs.getString("hours_unknown_days"))),
				lunch(rs, "lunch_week"),
				lunch(rs, "lunch_sat"),
				rs.getString("reception_week_text"),
				rs.getString("reception_sat_text"),
				note(rs, "sunday_note"),
				note(rs, "holiday_note"),
				rs.getObject("emergency_day", Boolean.class),
				rs.getObject("emergency_night", Boolean.class),
				rs.getObject("parking_capacity", Integer.class),
				rs.getObject("parking_fee", Boolean.class),
				rs.getString("parking_note"));
	}

	private static Lunch lunch(ResultSet rs, String prefix) throws SQLException {
		return new Lunch(LunchKind.valueOf(rs.getString(prefix + "_kind")),
				rs.getObject(prefix + "_start", Integer.class),
				rs.getObject(prefix + "_end", Integer.class),
				rs.getString(prefix + "_text"));
	}

	private static Note note(ResultSet rs, String prefix) throws SQLException {
		String kind = rs.getString(prefix + "_kind");
		return new Note(kind == null ? null : NoteKind.valueOf(kind), rs.getString(prefix + "_text"));
	}

}
