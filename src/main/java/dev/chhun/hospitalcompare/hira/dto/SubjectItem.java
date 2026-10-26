package dev.chhun.hospitalcompare.hira.dto;

/**
 * 진료과목정보(getDgsbjtInfo2.8)의 item. 선택진료의사 수(cdiagDrCnt)는 쓰지 않아 받지 않는다.
 *
 * @param dgsbjtPrSdrCnt 과목별 전문의 수(검증 전 문자열)
 */
public record SubjectItem(String dgsbjtCd, String dgsbjtCdNm, String dgsbjtPrSdrCnt) {
}
