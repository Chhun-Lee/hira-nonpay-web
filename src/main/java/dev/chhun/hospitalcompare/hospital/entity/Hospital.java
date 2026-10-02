package dev.chhun.hospitalcompare.hospital.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * 스냅샷 하나에 속한 의료기관 한 곳. 필드 주석의 괄호는 병원정보서비스(getHospBasisList) 응답 필드명이다.
 * <p>
 * 적재는 collector의 JDBC batch upsert가 맡으므로 생성자를 열어 두지 않는다.
 * 엔티티는 테이블 정의(ddl-auto=update)와 조회에 쓴다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
		name = "uk_hospital_ykiho_snapshot", columnNames = {"ykiho", "snapshot_id"}))
public class Hospital {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 암호화 요양기호(ykiho). 상세·비급여·평가 API를 잇는 기관 키. */
	@Column(nullable = false, length = 128)
	private String ykiho;

	@Column(name = "snapshot_id", nullable = false)
	private Long snapshotId;

	/** 기관명(yadmNm) */
	@Column(nullable = false, length = 200)
	private String name;

	/** 종별코드(clCd). 예: 01 상급종합, 11 종합병원, 21 병원, 31 의원 */
	@Column(length = 10)
	private String clCd;

	/** 종별코드명(clCdNm) */
	@Column(length = 100)
	private String clCdNm;

	/** 시도코드(sidoCd) */
	@Column(length = 10)
	private String sidoCd;

	/** 시도명(sidoCdNm) */
	@Column(length = 100)
	private String sidoCdNm;

	/** 시군구코드(sgguCd). 수집 단위. */
	@Column(length = 10)
	private String sgguCd;

	/** 시군구명(sgguCdNm) */
	@Column(length = 100)
	private String sgguCdNm;

	/** 읍면동명(emdongNm) */
	@Column(length = 100)
	private String emdongNm;

	/** 주소(addr) */
	@Column(length = 500)
	private String address;

	/** 전화번호(telno) */
	@Column(length = 30)
	private String phone;

	/** 개설일자(estbDd, yyyyMMdd) */
	private LocalDate establishedDate;

	/** 위도(YPos). 누락된 기관이 있어 nullable. */
	private Double latitude;

	/** 경도(XPos). 누락된 기관이 있어 nullable. */
	private Double longitude;

	/** 의사 총수(drTotCnt) */
	private Integer doctorCount;

	protected Hospital() {
	}

	public Long getId() {
		return id;
	}

	public String getYkiho() {
		return ykiho;
	}

	public Long getSnapshotId() {
		return snapshotId;
	}

	public String getName() {
		return name;
	}

	public String getClCd() {
		return clCd;
	}

	public String getClCdNm() {
		return clCdNm;
	}

	public String getSidoCd() {
		return sidoCd;
	}

	public String getSidoCdNm() {
		return sidoCdNm;
	}

	public String getSgguCd() {
		return sgguCd;
	}

	public String getSgguCdNm() {
		return sgguCdNm;
	}

	public String getEmdongNm() {
		return emdongNm;
	}

	public String getAddress() {
		return address;
	}

	public String getPhone() {
		return phone;
	}

	public LocalDate getEstablishedDate() {
		return establishedDate;
	}

	public Double getLatitude() {
		return latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public Integer getDoctorCount() {
		return doctorCount;
	}

}
