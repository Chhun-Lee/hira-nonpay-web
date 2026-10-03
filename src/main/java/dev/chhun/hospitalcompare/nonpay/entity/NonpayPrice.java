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
 * 한 병원의 한 항목 가격(스냅샷별). 병원 이름과 좌표는 두지 않고 비교할 때 ACTIVE 병원 목록과 ykiho로 잇는다.
 * 유니크 키 순서(snapshot_id, npay_cd, ykiho)가 곧 "이 스냅샷의 이 항목" 조회와 스냅샷 삭제용 인덱스다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
		name = "uk_nonpay_price_snapshot_code_ykiho", columnNames = {"snapshot_id", "npay_cd", "ykiho"}))
public class NonpayPrice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "snapshot_id", nullable = false)
	private Long snapshotId;

	@Column(name = "npay_cd", nullable = false, length = 50)
	private String npayCd;

	@Column(nullable = false, length = 128)
	private String ykiho;

	/** 원 */
	@Column(nullable = false)
	private Long minPrice;

	/** 원. 등급·부위별로 여러 줄을 공개한 병원은 최소와 다르다 */
	@Column(nullable = false)
	private Long maxPrice;

	@Column(length = 10)
	private String clCd;

	@Column(length = 10)
	private String sidoCd;

	@Column(length = 10)
	private String sgguCd;

	private LocalDate adtFrDd;

	protected NonpayPrice() {
	}

}
