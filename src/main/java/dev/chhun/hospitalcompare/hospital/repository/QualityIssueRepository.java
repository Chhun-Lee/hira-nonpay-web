package dev.chhun.hospitalcompare.hospital.repository;

import dev.chhun.hospitalcompare.hospital.dto.QualityIssue;
import dev.chhun.hospitalcompare.hospital.entity.QualityIssueType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class QualityIssueRepository {

	private static final String INSERT =
			"INSERT INTO data_quality_issue (snapshot_id, ykiho, issue_type, raw_value) VALUES (?, ?, ?, ?)";
	private static final int RAW_VALUE_MAX = 1000;

	private final JdbcTemplate jdbcTemplate;

	public QualityIssueRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void saveAll(long snapshotId, List<QualityIssue> issues) {
		if (issues.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(INSERT, issues, issues.size(), (ps, issue) -> {
			ps.setLong(1, snapshotId);
			ps.setString(2, issue.ykiho());
			ps.setString(3, issue.type().name());
			String raw = issue.rawValue();
			ps.setString(4, raw == null || raw.length() <= RAW_VALUE_MAX ? raw : raw.substring(0, RAW_VALUE_MAX));
		});
	}

	public Map<QualityIssueType, Integer> countBySnapshot(long snapshotId) {
		Map<QualityIssueType, Integer> counts = new EnumMap<>(QualityIssueType.class);
		jdbcTemplate.query(
				"SELECT issue_type, COUNT(*) AS issue_count FROM data_quality_issue WHERE snapshot_id = ? GROUP BY issue_type",
				rs -> {
					counts.put(QualityIssueType.valueOf(rs.getString("issue_type")), rs.getInt("issue_count"));
				},
				snapshotId);
		return counts;
	}

}
