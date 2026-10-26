package dev.chhun.hospitalcompare.detail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JavaType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 한 기관의 세부정보(스냅샷별, getDtlInfo2.8). 세부정보가 있는 기관만 행이 있다.
 * 시각은 자정부터 센 분이고, 종료는 1440(24:00) 이하다. 적재·조회는 JDBC가 맡고 엔티티는 테이블 정의에 쓴다.
 * 열 이름은 DetailColumns와 같아야 한다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
		name = "uk_hospital_detail_snapshot_ykiho", columnNames = {"snapshot_id", "ykiho"}))
public class HospitalDetail {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "snapshot_id", nullable = false)
	private Long snapshotId;

	@Column(nullable = false, length = 128)
	private String ykiho;

	// 요일별 진료 시작·종료(trmt{Mon..Sun}{Start,End}). 읽지 못했거나 칸이 없으면 null
	private Integer monOpen;
	private Integer monClose;
	private Integer tueOpen;
	private Integer tueClose;
	private Integer wedOpen;
	private Integer wedClose;
	private Integer thuOpen;
	private Integer thuClose;
	private Integer friOpen;
	private Integer friClose;
	private Integer satOpen;
	private Integer satClose;
	private Integer sunOpen;
	private Integer sunClose;

	/** 시각 칸은 있었지만 읽지 못한 요일(DayOfWeek 이름, 쉼표 구분). 없으면 null */
	@Column(length = 80)
	private String hoursUnknownDays;

	@JavaType(LunchKindJavaType.class)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private LunchKind lunchWeekKind;

	private Integer lunchWeekStart;

	private Integer lunchWeekEnd;

	/** 평일 점심 원문(lunchWeek) */
	@Column(length = 500)
	private String lunchWeekText;

	@JavaType(LunchKindJavaType.class)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private LunchKind lunchSatKind;

	private Integer lunchSatStart;

	private Integer lunchSatEnd;

	/** 토요일 점심 원문(lunchSat) */
	@Column(length = 500)
	private String lunchSatText;

	/** 평일 접수 원문(rcvWeek) */
	@Column(length = 500)
	private String receptionWeekText;

	/** 토요일 접수 원문(rcvSat) */
	@Column(length = 500)
	private String receptionSatText;

	@JavaType(NoteKindJavaType.class)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private NoteKind sundayNoteKind;

	/** 일요일 휴진 안내 원문(noTrmtSun) */
	@Column(length = 500)
	private String sundayNoteText;

	@JavaType(NoteKindJavaType.class)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private NoteKind holidayNoteKind;

	/** 공휴일 휴진 안내 원문(noTrmtHoli) */
	@Column(length = 500)
	private String holidayNoteText;

	/** 주간 응급실 운영(emyDayYn) */
	private Boolean emergencyDay;

	/** 야간 응급실 운영(emyNgtYn) */
	private Boolean emergencyNight;

	/** 주차 가능 대수(parkQty) */
	private Integer parkingCapacity;

	/** 주차비 부담 여부(parkXpnsYn) */
	private Boolean parkingFee;

	/** 주차 기타 안내(parkEtc) */
	@Column(length = 500)
	private String parkingNote;

	protected HospitalDetail() {
	}

}
