package dev.chhun.hospitalcompare.nonpay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * 비급여 항목 코드 하나(스냅샷별). 적재·조회는 JDBC가 맡고, 엔티티는 테이블 정의에 쓴다.
 * 열 길이는 활용 가이드의 항목 크기를 따른다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
		name = "uk_nonpay_item_snapshot_code", columnNames = {"snapshot_id", "npay_cd"}))
public class NonpayItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "snapshot_id", nullable = false)
	private Long snapshotId;

	@Column(name = "npay_cd", nullable = false, length = 50)
	private String npayCd;

	@Column(length = 400)
	private String npayKorNm;

	@Column(length = 10)
	private String mdivCd;

	@Column(length = 400)
	private String mdivCdNm;

	@Column(length = 10)
	private String sdivCd;

	@Column(length = 400)
	private String sdivCdNm;

	@Column(length = 10)
	private String dtlDivCd;

	@Column(length = 400)
	private String dtlDivCdNm;

	private LocalDate adtFrDd;

	/** 무기한이면 9999-12-31 */
	private LocalDate adtEndDd;

	protected NonpayItem() {
	}

}
