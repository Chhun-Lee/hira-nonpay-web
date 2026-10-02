package dev.chhun.hospitalcompare.hospital;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
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

	// Hibernate는 MySQL에서 enum을 네이티브 ENUM 컬럼으로 만든다. ddl-auto=update는 기존 컬럼을 고치지 않아
	// 상태가 늘면 INSERT가 실패하므로 VARCHAR로 둔다.
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private SnapshotStatus status;

	/** 적재된 기관 수 */
	@Column(nullable = false)
	private int recordCount;

	protected Snapshot() {
	}

	public Snapshot(LocalDate baseDate, SnapshotStatus status) {
		this.baseDate = baseDate;
		this.status = status;
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

}
