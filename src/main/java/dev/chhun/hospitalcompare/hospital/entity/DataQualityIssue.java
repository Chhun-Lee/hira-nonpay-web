package dev.chhun.hospitalcompare.hospital.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.hibernate.annotations.JavaType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 수집 중 발견한 데이터 품질 문제 한 건. 데이터 품질 리포트(7단계)의 재료다.
 * 적재는 QualityIssueRepository의 JDBC batch가 맡고, 엔티티는 테이블 정의에 쓴다.
 */
@Entity
public class DataQualityIssue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "snapshot_id", nullable = false)
	private Long snapshotId;

	/** 필수값 누락이면 비어 있을 수 있다 */
	@Column(length = 128)
	private String ykiho;

	// Snapshot.status와 같은 이유로 CHECK 제약이 없는 VARCHAR로 저장한다.
	@JavaType(QualityIssueTypeJavaType.class)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 40)
	private QualityIssueType issueType;

	/** 문제가 된 원본 값 */
	@Column(length = 1000)
	private String rawValue;

	protected DataQualityIssue() {
	}

	public Long getId() {
		return id;
	}

	public Long getSnapshotId() {
		return snapshotId;
	}

	public String getYkiho() {
		return ykiho;
	}

	public QualityIssueType getIssueType() {
		return issueType;
	}

	public String getRawValue() {
		return rawValue;
	}

}
