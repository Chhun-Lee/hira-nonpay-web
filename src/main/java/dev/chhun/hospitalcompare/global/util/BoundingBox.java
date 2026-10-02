package dev.chhun.hospitalcompare.global.util;

/**
 * 반경 원을 덮는 위경도 사각형. 단순 범위 조건으로 후보를 먼저 좁히고, 원 밖은 구면 거리로 다시 거른다.
 */
public record BoundingBox(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {

	/** MySQL ST_Distance_Sphere가 쓰는 기본 지구 반지름(m). 거리 계산과 같은 기준으로 박스를 잡는다. */
	static final double EARTH_RADIUS_METERS = 6_370_986;

	public static BoundingBox around(double latitude, double longitude, double radiusMeters) {
		double angularRadius = radiusMeters / EARTH_RADIUS_METERS;
		double latitudeDelta = Math.toDegrees(angularRadius);
		// 같은 거리라도 위도가 높을수록 경도 폭이 넓어진다.
		double longitudeDelta = Math.toDegrees(
				Math.asin(Math.sin(angularRadius) / Math.cos(Math.toRadians(latitude))));
		return new BoundingBox(
				latitude - latitudeDelta, latitude + latitudeDelta,
				longitude - longitudeDelta, longitude + longitudeDelta);
	}

}
