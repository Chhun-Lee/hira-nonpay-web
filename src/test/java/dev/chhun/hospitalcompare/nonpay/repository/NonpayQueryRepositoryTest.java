package dev.chhun.hospitalcompare.nonpay.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import dev.chhun.hospitalcompare.nonpay.dto.NearbyPrice;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayItemSummary;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayStatRecord;
import dev.chhun.hospitalcompare.nonpay.entity.StatDimension;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 기준점 (37.5, 127.0), 반경 500m.
 * Y-NEAR 약 100m(북), Y-EAST 약 300m(동)은 반경 안이다. Y-CORNER(약 627m)는 바운딩 박스에는 들지만 반경 밖이다.
 * 빠져야 하는 것: Y-FAR(먼 곳), Y-OLD(지난 병원 목록에만 있음), Y-GHOST(병원 목록에 없음), 시험 실행 스냅샷의 가격.
 */
@ApiIntegrationTest
class NonpayQueryRepositoryTest {

	private static final String MRI = "HE1110000";

	@Autowired
	NonpayQueryRepository repository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	long hospitalActive;
	long nonpayActive;
	long nonpayTrial;

	@BeforeEach
	void setUp() {
		for (String table : List.of("nonpay_price", "nonpay_item", "nonpay_stat", "hospital", "snapshot")) {
			jdbcTemplate.update("delete from " + table);
		}
		hospitalActive = insertSnapshot("HOSPITAL_LIST", "ACTIVE");
		long hospitalRetired = insertSnapshot("HOSPITAL_LIST", "RETIRED");
		nonpayActive = insertSnapshot("NONPAY", "ACTIVE");
		nonpayTrial = insertSnapshot("NONPAY", "TRIAL");

		insertHospital(hospitalActive, "Y-NEAR", "가까운병원", 37.5009, 127.0);
		insertHospital(hospitalActive, "Y-EAST", "동쪽병원", 37.5, 127.0034);
		insertHospital(hospitalActive, "Y-CORNER", "모서리병원", 37.5040, 127.0050);
		insertHospital(hospitalActive, "Y-FAR", "먼병원", 37.6, 127.0);
		insertHospital(hospitalRetired, "Y-OLD", "지난목록병원", 37.5005, 127.0);

		insertItem(nonpayActive, MRI, "자기공명영상진단료(MRI-기본검사)/척추-요천추/일반", "자기공명영상진단료(MRI-기본검사)");
		insertItem(nonpayActive, "ABZ010001", "상급병실료/1인실", "상급병실료");
		insertItem(nonpayActive, "PCT0001", "검사료/100%_특수", "검사료");
		insertItem(nonpayActive, "NOPRICE01", "자기공명영상진단료(MRI-기본검사)/가격없음", "자기공명영상진단료(MRI-기본검사)");

		insertPrice(nonpayActive, MRI, "Y-NEAR", 400000, 450000, "110000");
		insertPrice(nonpayActive, MRI, "Y-EAST", 300000, 300000, "110000");
		insertPrice(nonpayActive, MRI, "Y-CORNER", 500000, 500000, "110000");
		insertPrice(nonpayActive, MRI, "Y-FAR", 500000, 500000, "110000");
		insertPrice(nonpayActive, MRI, "Y-OLD", 1, 1, "110000");
		insertPrice(nonpayActive, MRI, "Y-GHOST", 1, 1, "110000");
		insertPrice(nonpayActive, "ABZ010001", "Y-NEAR", 200000, 200000, "110000");
		insertPrice(nonpayActive, "PCT0001", "Y-NEAR", 1000, 1000, "110000");
		insertPrice(nonpayTrial, MRI, "Y-EAST", 1, 1, "110000");

		jdbcTemplate.update("""
				insert into nonpay_stat (snapshot_id, npay_cd, dimension, dim_key, min_price, max_price, avg_price,
				                         median_price, std_date)
				values (?, ?, 'CL_TYPE', 'All', 250000, 1001000, 480000, 456450, '2026-09-03'),
				       (?, ?, 'SIDO', 'Sl', 400000, 1001000, 520000, 500000, '2026-09-03')
				""", nonpayActive, MRI, nonpayActive, MRI);
	}

	@Test
	void 반경_안_가격을_두_출처의_ACTIVE에서만_읽는다() {
		List<NearbyPrice> prices = repository.findNearbyPrices(nonpayActive, hospitalActive, MRI, 37.5, 127.0, 500);

		assertThat(prices).extracting(NearbyPrice::ykiho).containsExactlyInAnyOrder("Y-NEAR", "Y-EAST");
		NearbyPrice near = prices.stream().filter(price -> price.ykiho().equals("Y-NEAR")).findFirst().orElseThrow();
		assertThat(near.name()).isEqualTo("가까운병원");
		assertThat(near.minPrice()).isEqualTo(400000);
		assertThat(near.maxPrice()).isEqualTo(450000);
		assertThat(near.sidoCd()).isEqualTo("110000");
		assertThat(near.distanceMeters()).isBetween(98, 102);
		NearbyPrice east = prices.stream().filter(price -> price.ykiho().equals("Y-EAST")).findFirst().orElseThrow();
		assertThat(east.minPrice()).isEqualTo(300000);
	}

	@Test
	void 항목_검색은_부분일치이고_공개_병원이_많은_순이며_같으면_코드_순이다() {
		List<NonpayItemSummary> items = repository.searchItems(nonpayActive, "료", 20);

		assertThat(items).extracting(NonpayItemSummary::code).containsExactly(MRI, "ABZ010001", "PCT0001");
		assertThat(items.getFirst().hospitalCount()).isEqualTo(6);
		assertThat(items.getFirst().category()).isEqualTo("자기공명영상진단료(MRI-기본검사)");
	}

	@Test
	void 가격을_공개한_병원이_없는_항목은_검색에_나오지_않는다() {
		assertThat(repository.searchItems(nonpayActive, "MRI", 20)).extracting(NonpayItemSummary::code)
				.containsExactly(MRI);
	}

	@Test
	void 검색어의_퍼센트와_밑줄은_글자_그대로_찾는다() {
		assertThat(repository.searchItems(nonpayActive, "%", 20)).extracting(NonpayItemSummary::code)
				.containsExactly("PCT0001");
		assertThat(repository.searchItems(nonpayActive, "_", 20)).extracting(NonpayItemSummary::code)
				.containsExactly("PCT0001");
		assertThat(repository.searchItems(nonpayActive, "!", 20)).isEmpty();
	}

	@Test
	void 검색_결과는_요청한_개수까지만이다() {
		assertThat(repository.searchItems(nonpayActive, "료", 2)).hasSize(2);
	}

	@Test
	void 항목_하나는_가격이_없어도_찾고_모르는_코드는_비어_있다() {
		assertThat(repository.findItem(nonpayActive, "NOPRICE01")).get()
				.extracting(NonpayItemSummary::hospitalCount).isEqualTo(0L);
		assertThat(repository.findItem(nonpayActive, MRI)).get()
				.extracting(NonpayItemSummary::hospitalCount).isEqualTo(6L);
		assertThat(repository.findItem(nonpayActive, "ZZZ")).isEmpty();
	}

	@Test
	void 여러_항목은_가격을_공개한_병원이_있는_것만_찾는다() {
		assertThat(repository.findItems(nonpayActive, List.of("ABZ010001", "NOPRICE01", "ZZZ")))
				.extracting(NonpayItemSummary::code).containsExactly("ABZ010001");
		assertThat(repository.findItems(nonpayActive, List.of())).isEmpty();
	}

	@Test
	void 항목의_종별_시도_통계를_읽는다() {
		List<NonpayStatRecord> stats = repository.findStats(nonpayActive, MRI);

		assertThat(stats).extracting(NonpayStatRecord::dimension, NonpayStatRecord::dimKey)
				.containsExactly(tuple(StatDimension.CL_TYPE, "All"), tuple(StatDimension.SIDO, "Sl"));
		assertThat(stats.getFirst().medianPrice()).isEqualTo(456450L);
		assertThat(stats.getFirst().stdDate()).isEqualTo(LocalDate.of(2026, 9, 3));
	}

	private long insertSnapshot(String source, String status) {
		jdbcTemplate.update("insert into snapshot (source, status, base_date, record_count) values (?, ?, '2026-10-03', 0)",
				source, status);
		return jdbcTemplate.queryForObject("select id from snapshot where source = ? and status = ?", Long.class,
				source, status);
	}

	private void insertHospital(long snapshotId, String ykiho, String name, double lat, double lng) {
		jdbcTemplate.update("""
				insert into hospital (snapshot_id, ykiho, name, cl_cd, cl_cd_nm, address, phone, latitude, longitude, sido_cd)
				values (?, ?, ?, '21', '병원', '서울 어딘가', '02-000-0000', ?, ?, '110000')
				""", snapshotId, ykiho, name, lat, lng);
	}

	private void insertItem(long snapshotId, String code, String name, String category) {
		jdbcTemplate.update("insert into nonpay_item (snapshot_id, npay_cd, npay_kor_nm, mdiv_cd_nm) values (?, ?, ?, ?)",
				snapshotId, code, name, category);
	}

	private void insertPrice(long snapshotId, String code, String ykiho, long min, long max, String sidoCd) {
		jdbcTemplate.update("""
				insert into nonpay_price (snapshot_id, npay_cd, ykiho, min_price, max_price, sido_cd)
				values (?, ?, ?, ?, ?, ?)
				""", snapshotId, code, ykiho, min, max, sidoCd);
	}

}
