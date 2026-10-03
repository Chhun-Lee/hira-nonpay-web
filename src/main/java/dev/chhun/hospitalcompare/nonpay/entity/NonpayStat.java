package dev.chhun.hospitalcompare.nonpay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import org.hibernate.annotations.JavaType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 항목 하나의 기준값(스냅샷별). API의 가로형 통계(칸 수십 개)를 (dimension, dim_key) 세로형으로 펼쳐 담는다.
 * 시도가 바뀌어도 테이블은 그대로다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
		name = "uk_nonpay_stat", columnNames = {"snapshot_id", "npay_cd", "dimension", "dim_key"}))
public class NonpayStat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "snapshot_id", nullable = false)
	private Long snapshotId;

	@Column(name = "npay_cd", nullable = false, length = 50)
	private String npayCd;

	// Snapshot.status와 같은 이유로 CHECK 제약이 없는 VARCHAR로 저장한다.
	@JavaType(StatDimensionJavaType.class)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private StatDimension dimension;

	/** API 접미사 그대로(All, Usgh, Sl, Sj …) */
	@Column(name = "dim_key", nullable = false, length = 10)
	private String dimKey;

	private Long minPrice;

	private Long maxPrice;

	private Long avgPrice;

	private Long medianPrice;

	private LocalDate stdDate;

	protected NonpayStat() {
	}

}
