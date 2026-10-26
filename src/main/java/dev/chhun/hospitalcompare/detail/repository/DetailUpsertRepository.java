package dev.chhun.hospitalcompare.detail.repository;

import dev.chhun.hospitalcompare.detail.dto.HospitalDetailRecord;
import dev.chhun.hospitalcompare.detail.dto.HospitalSubjectRecord;
import dev.chhun.hospitalcompare.detail.dto.Lunch;
import dev.chhun.hospitalcompare.detail.dto.Note;
import dev.chhun.hospitalcompare.detail.dto.OpenRange;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 세부정보·진료과목을 JDBC batch로 upsert한다. 멱등 키는 (snapshot_id, ykiho[, subject_code])다. */
@Repository
public class DetailUpsertRepository {

	private static final String UPSERT_DETAIL = "INSERT INTO hospital_detail (snapshot_id, ykiho, "
			+ String.join(", ", DetailColumns.DATA) + ") VALUES (?, ?, "
			+ String.join(", ", Collections.nCopies(DetailColumns.DATA.size(), "?")) + ") AS new"
			+ " ON DUPLICATE KEY UPDATE "
			+ DetailColumns.DATA.stream().map(column -> column + " = new." + column).collect(Collectors.joining(", "));

	private static final String UPSERT_SUBJECT = """
			INSERT INTO hospital_subject (snapshot_id, ykiho, subject_code, subject_name, specialist_count)
			VALUES (?, ?, ?, ?, ?) AS new
			ON DUPLICATE KEY UPDATE subject_name = new.subject_name, specialist_count = new.specialist_count
			""";

	private final JdbcTemplate jdbcTemplate;

	public DetailUpsertRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void upsertDetails(long snapshotId, List<HospitalDetailRecord> records) {
		if (records.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(UPSERT_DETAIL, records, records.size(), (ps, record) -> {
			ps.setLong(1, snapshotId);
			ps.setString(2, record.ykiho());
			bind(ps, 3, values(record));
		});
	}

	@Transactional
	public void upsertSubjects(long snapshotId, List<HospitalSubjectRecord> records) {
		if (records.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(UPSERT_SUBJECT, records, records.size(), (ps, record) -> {
			ps.setLong(1, snapshotId);
			ps.setString(2, record.ykiho());
			ps.setString(3, record.code());
			ps.setString(4, record.name());
			ps.setObject(5, record.specialists());
		});
	}

	public long countDetails(long snapshotId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM hospital_detail WHERE snapshot_id = ?", Long.class, snapshotId);
		return count == null ? 0 : count;
	}

	/** DetailColumns.DATA와 같은 순서의 값 */
	private static List<Object> values(HospitalDetailRecord record) {
		List<Object> values = new ArrayList<>(DetailColumns.DATA.size());
		for (DayOfWeek day : DayOfWeek.values()) {
			OpenRange range = record.hours().days().get(day);
			values.add(range == null ? null : range.open());
			values.add(range == null ? null : range.close());
		}
		values.add(DetailColumns.days(record.hours().unknownDays()));
		addLunch(values, record.lunchWeek());
		addLunch(values, record.lunchSat());
		values.add(record.receptionWeek());
		values.add(record.receptionSat());
		addNote(values, record.sundayNote());
		addNote(values, record.holidayNote());
		values.add(record.emergencyDay());
		values.add(record.emergencyNight());
		values.add(record.parkingCapacity());
		values.add(record.parkingFee());
		values.add(record.parkingNote());
		return values;
	}

	private static void addLunch(List<Object> values, Lunch lunch) {
		values.add(lunch.kind().name());
		values.add(lunch.start());
		values.add(lunch.end());
		values.add(lunch.text());
	}

	private static void addNote(List<Object> values, Note note) {
		values.add(note.kind() == null ? null : note.kind().name());
		values.add(note.text());
	}

	private static void bind(PreparedStatement ps, int firstIndex, List<Object> values) throws SQLException {
		for (int i = 0; i < values.size(); i++) {
			ps.setObject(firstIndex + i, values.get(i));
		}
	}

}
