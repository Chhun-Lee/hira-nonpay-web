package dev.chhun.hospitalcompare.snapshot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import org.hibernate.annotations.ColumnDefault;
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

	// 3-2에서 추가한 열. 3-1 DB의 기존 행은 ddl-auto가 열을 추가할 때 기본값(병원 목록)으로 채워진다
	// (SnapshotSourceEvolutionTest가 지킨다). 값 목록 CHECK가 없는 것은 status와 같은 이유다.
	@JavaType(SnapshotSourceJavaType.class)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@ColumnDefault("'HOSPITAL_LIST'")
	@Column(nullable = false, length = 20)
	private SnapshotSource source;

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

	/** FAILED·TRIAL이 된 이유 */
	@Column(length = 500)
	private String failureReason;

	protected Snapshot() {
	}

	public Snapshot(SnapshotSource source, LocalDate baseDate, SnapshotStatus status) {
		this.source = source;
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
		this.failureReason = truncate(reason);
	}

	/** 시험 실행을 마쳤다. 화면에 나가지 않는다. */
	public void endTrial(String reason) {
		this.status = SnapshotStatus.TRIAL;
		this.failureReason = truncate(reason);
	}

	private static String truncate(String reason) {
		return reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500);
	}

	public Long getId() {
		return id;
	}

	public SnapshotSource getSource() {
		return source;
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
