package dev.chhun.hospitalcompare.hospital.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import org.hibernate.annotations.JavaType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 한 번의 수집 결과 버전. hospital 행은 snapshot_id로 여기에 속한다.
 */
@Entity
public class Snapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 데이터 기준일. 화면에 함께 표시한다. */
	@Column(nullable = false)
	private LocalDate baseDate;

	// Hibernate는 enum 컬럼에 값 목록 CHECK 제약을 만들고, ddl-auto=update는 기존 제약을 고치지 않는다.
	// 그러면 상태를 추가한 뒤 기존 DB에서 INSERT가 실패한다. 평범한 VARCHAR로 저장해 CHECK를 없앴으므로
	// 상태를 추가해도 DDL 변경이 필요 없다(EnumColumnSchemaTest가 지킨다).
	@JavaType(SnapshotStatusJavaType.class)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private SnapshotStatus status;

	/** 적재된 기관 수 */
	@Column(nullable = false)
	private int recordCount;

	/** FAILED가 된 이유 */
	@Column(length = 500)
	private String failureReason;

	protected Snapshot() {
	}

	public Snapshot(LocalDate baseDate, SnapshotStatus status) {
		this.baseDate = baseDate;
		this.status = status;
	}

	/** 검증을 통과한 STAGE를 화면용으로 바꾼다. */
	public void activate(int recordCount) {
		this.status = SnapshotStatus.ACTIVE;
		this.recordCount = recordCount;
		this.failureReason = null;
	}

	public void retire() {
		this.status = SnapshotStatus.RETIRED;
	}

	public void fail(String reason) {
		this.status = SnapshotStatus.FAILED;
		this.failureReason = reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500);
	}

	public Long getId() {
		return id;
	}

	public LocalDate getBaseDate() {
		return baseDate;
	}

	public SnapshotStatus getStatus() {
		return status;
	}

	public int getRecordCount() {
		return recordCount;
	}

	public String getFailureReason() {
		return failureReason;
	}

}
