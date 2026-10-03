package dev.chhun.hospitalcompare.snapshot.entity;

/**
 * 스냅샷이 담는 데이터의 출처. 출처마다 STAGE → ACTIVE 전환과 정리를 따로 한다.
 */
public enum SnapshotSource {

	/** 병원정보서비스 전국 목록(hospital 테이블) */
	HOSPITAL_LIST,

	/** 비급여진료비정보서비스(nonpay_item, nonpay_price, nonpay_stat 테이블) */
	NONPAY

}
