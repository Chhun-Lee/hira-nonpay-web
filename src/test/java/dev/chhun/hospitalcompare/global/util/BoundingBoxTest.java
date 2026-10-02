package dev.chhun.hospitalcompare.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class BoundingBoxTest {

	@Test
	void 반경을_덮는_위경도_범위를_구한다() {
		// 위도 37.5도에서 1km: 위도 ±0.008993도, 경도는 cos(위도)만큼 넓어져 ±0.011336도
		BoundingBox box = BoundingBox.around(37.5, 127.0, 1_000);

		assertThat(box.minLatitude()).isCloseTo(37.491007, within(1e-6));
		assertThat(box.maxLatitude()).isCloseTo(37.508993, within(1e-6));
		assertThat(box.minLongitude()).isCloseTo(126.988664, within(1e-6));
		assertThat(box.maxLongitude()).isCloseTo(127.011336, within(1e-6));
	}

}
