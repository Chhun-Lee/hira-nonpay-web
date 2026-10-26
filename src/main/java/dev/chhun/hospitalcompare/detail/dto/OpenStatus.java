package dev.chhun.hospitalcompare.detail.dto;

/**
 * 지금 진료 상태와 화면에 그대로 쓰는 짧은 사유(진료 중이면 null).
 */
public record OpenStatus(OpenState status, String reason) {

	public static final OpenStatus OPEN = new OpenStatus(OpenState.OPEN, null);

	public static final OpenStatus NO_INFO = unknown("진료시간 정보 없음");

	public static OpenStatus closed(String reason) {
		return new OpenStatus(OpenState.CLOSED, reason);
	}

	public static OpenStatus unknown(String reason) {
		return new OpenStatus(OpenState.UNKNOWN, reason);
	}

}
