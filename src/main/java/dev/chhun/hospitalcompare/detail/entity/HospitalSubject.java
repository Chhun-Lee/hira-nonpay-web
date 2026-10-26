package dev.chhun.hospitalcompare.detail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 한 기관의 진료과목 하나와 그 과목의 전문의 수(스냅샷별, getDgsbjtInfo2.8).
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
		name = "uk_hospital_subject_snapshot_ykiho_code", columnNames = {"snapshot_id", "ykiho", "subject_code"}))
public class HospitalSubject {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "snapshot_id", nullable = false)
	private Long snapshotId;

	@Column(nullable = false, length = 128)
	private String ykiho;

	/** 진료과목코드(dgsbjtCd) */
	@Column(name = "subject_code", nullable = false, length = 10)
	private String subjectCode;

	/** 진료과목명(dgsbjtCdNm) */
	@Column(length = 100)
	private String subjectName;

	/** 과목별 전문의 수(dgsbjtPrSdrCnt). 정수가 아니면 null */
	private Integer specialistCount;

	protected HospitalSubject() {
	}

}
