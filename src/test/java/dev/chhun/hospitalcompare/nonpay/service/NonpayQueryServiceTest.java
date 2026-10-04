package dev.chhun.hospitalcompare.nonpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.chhun.hospitalcompare.ApiIntegrationTest;
import dev.chhun.hospitalcompare.nonpay.dto.LocalPriceStats;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayItemResponse;
import dev.chhun.hospitalcompare.nonpay.dto.PriceCompareResponse;
import dev.chhun.hospitalcompare.nonpay.dto.PricedHospital;
import dev.chhun.hospitalcompare.nonpay.dto.StatValues;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 기준점 (37.5, 127.0), 반경 500m. 허리 MRI(HE1110000) 가격:
 * Y-A 약 100m 300,000원(전남광주 360000), Y-C 약 200m 100,000원(서울), Y-B 약 300m 100,000~200,000원(서울).
 */
@ApiIntegrationTest
class NonpayQueryServiceTest {

	private static final String MRI = "HE1110000";

	@Autowired
	NonpayQueryService service;

	@Autowired
	JdbcTemplate jdbcTemplate;

	long hospitalActive;
	long nonpayActive;

	@BeforeEach
	void setUp() {
		for (String table : List.of("nonpay_price", "nonpay_item", "nonpay_stat", "hospital", "snapshot")) {
			jdbcTemplate.update("delete from " + table);
		}
		hospitalActive = insertSnapshot("HOSPITAL_LIST", "ACTIVE", "2026-10-02");
		nonpayActive = insertSnapshot("NONPAY", "ACTIVE", "2026-10-03");

		insertHospital(hospitalActive, "Y-A", "가병원", 37.5009, 127.0);
		insertHospital(hospitalActive, "Y-B", "나병원", 37.5, 127.0034);
		insertHospital(hospitalActive, "Y-C", "다병원", 37.4982, 127.0);

		insertItem(nonpayActive, MRI, "자기공명영상진단료(MRI-기본검사)/척추-요천추/일반");
		insertPrice(nonpayActive, MRI, "Y-A", 300000, 300000, "360000");
		insertPrice(nonpayActive, MRI, "Y-B", 100000, 200000, "110000");
		insertPrice(nonpayActive, MRI, "Y-C", 100000, 100000, "110000");

		insertStat(nonpayActive, MRI, "CL_TYPE", "Hosp", 90000);
		insertStat(nonpayActive, MRI, "CL_TYPE", "All", 456450);
		insertStat(nonpayActive, MRI, "CL_TYPE", "Usgh", 700000);
		insertStat(nonpayActive, MRI, "SIDO", "Cln", 410000);
		insertStat(nonpayActive, MRI, "SIDO", "Sl", 500000);
	}

	@Test
	void 가격순은_최소가_최대가_거리_순이다() {
		assertThat(compare(500, PriceSort.PRICE).hospitals()).extracting(PricedHospital::ykiho)
				.containsExactly("Y-C", "Y-B", "Y-A");
	}

	@Test
	void 거리순은_가까운_순이다() {
		assertThat(compare(500, PriceSort.DISTANCE).hospitals()).extracting(PricedHospital::ykiho)
				.containsExactly("Y-A", "Y-C", "Y-B");
	}

	@Test
	void 반경_기준값은_병원별_최저가로_계산한다() {
		// 최저가 100,000 100,000 300,000 → 중앙값 100,000, 평균 166,666.67 → 166,667, 최고는 최대가 300,000
		assertThat(compare(500, PriceSort.PRICE).local())
				.isEqualTo(new LocalPriceStats(3, 100000, 166667, 100000, 300000));
	}

	@Test
	void 우리_시도는_지도_중심에_가장_가까운_병원의_시도다() {
		StatValues sido = compare(500, PriceSort.PRICE).reference().sido();

		assertThat(sido.key()).isEqualTo("Cln");
		assertThat(sido.name()).isEqualTo("전남광주");
		assertThat(sido.median()).isEqualTo(410000L);
	}

	@Test
	void 모르는_시도_코드면_시도_기준값만_비운다() {
		jdbcTemplate.update("update nonpay_price set sido_cd = '999999' where ykiho = 'Y-A'");

		PriceCompareResponse response = compare(500, PriceSort.PRICE);

		assertThat(response.reference().sido()).isNull();
		assertThat(response.reference().nationwide()).isNotNull();
	}

	@Test
	void 전국은_종별_All이고_종별은_정해진_순서다() {
		PriceCompareResponse response = compare(500, PriceSort.PRICE);

		assertThat(response.reference().nationwide().name()).isEqualTo("전국");
		assertThat(response.reference().nationwide().median()).isEqualTo(456450L);
		assertThat(response.reference().byType()).extracting(StatValues::key, StatValues::name)
				.containsExactly(tuple("Usgh", "상급종합"), tuple("Hosp", "병원"));
		assertThat(response.reference().stdDate()).isEqualTo(LocalDate.of(2026, 9, 3));
	}

	@Test
	void 기준일과_항목을_함께_준다() {
		PriceCompareResponse response = compare(500, PriceSort.PRICE);

		assertThat(response.baseDate()).isEqualTo(
				new PriceCompareResponse.BaseDates(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3)));
		assertThat(response.item().code()).isEqualTo(MRI);
		assertThat(response.item().detail()).isEqualTo("척추-요천추/일반");
		assertThat(response.item().hospitalCount()).isEqualTo(3);
		assertThat(response.total()).isEqualTo(3);
	}

	@Test
	void 오백_곳에서_자르되_total과_반경_기준값은_반경_안_전체로_센다() {
		List<Object[]> hospitals = IntStream.range(0, 501)
				.mapToObj(i -> new Object[] {hospitalActive, "Y-MANY-" + i, "많은병원" + i, 37.5 + i * 0.000001, 127.0})
				.toList();
		jdbcTemplate.batchUpdate("""
				insert into hospital (snapshot_id, ykiho, name, cl_cd, cl_cd_nm, latitude, longitude)
				values (?, ?, ?, '21', '병원', ?, ?)
				""", hospitals);
		insertItem(nonpayActive, "ABZ010001", "상급병실료/1인실");
		List<Object[]> prices = IntStream.range(0, 501)
				.mapToObj(i -> new Object[] {nonpayActive, "Y-MANY-" + i, 100000 + i, 100000 + i})
				.toList();
		jdbcTemplate.batchUpdate("""
				insert into nonpay_price (snapshot_id, npay_cd, ykiho, min_price, max_price, sido_cd)
				values (?, 'ABZ010001', ?, ?, ?, '110000')
				""", prices);

		PriceCompareResponse response = service.comparePrices("ABZ010001", 37.5, 127.0, 500, PriceSort.PRICE)
				.orElseThrow();

		assertThat(response.hospitals()).hasSize(500);
		assertThat(response.total()).isEqualTo(501);
		assertThat(response.local().count()).isEqualTo(501);
		assertThat(response.local().max()).isEqualTo(100500L);
	}

	@Test
	void 반경_안에_병원이_없으면_목록과_반경_기준값과_시도는_비고_전국은_준다() {
		PriceCompareResponse response = service.comparePrices(MRI, 37.0, 127.0, 500, PriceSort.PRICE).orElseThrow();

		assertThat(response.hospitals()).isEmpty();
		assertThat(response.total()).isZero();
		assertThat(response.local()).isNull();
		assertThat(response.reference().sido()).isNull();
		assertThat(response.reference().nationwide()).isNotNull();
	}

	@Test
	void 반경_안_병원이_세_곳_미만이면_반경_기준값은_비어_있다() {
		jdbcTemplate.update("delete from nonpay_price where ykiho = 'Y-B'");

		assertThat(compare(500, PriceSort.PRICE).local()).isNull();
	}

	@Test
	void 병원_목록_ACTIVE가_없으면_목록과_시도는_비고_통계는_준다() {
		jdbcTemplate.update("delete from snapshot where source = 'HOSPITAL_LIST'");

		PriceCompareResponse response = compare(500, PriceSort.PRICE);

		assertThat(response.baseDate()).isEqualTo(new PriceCompareResponse.BaseDates(null, LocalDate.of(2026, 10, 3)));
		assertThat(response.hospitals()).isEmpty();
		assertThat(response.total()).isZero();
		assertThat(response.local()).isNull();
		assertThat(response.reference().sido()).isNull();
		assertThat(response.reference().nationwide()).isNotNull();
	}

	@Test
	void 모르는_항목이면_비어_있다() {
		assertThat(service.comparePrices("ZZZ", 37.5, 127.0, 500, PriceSort.PRICE)).isEmpty();
	}

	@Test
	void 비급여_ACTIVE가_없으면_빈_결과다() {
		jdbcTemplate.update("delete from snapshot where source = 'NONPAY'");

		PriceCompareResponse response = service.comparePrices(MRI, 37.5, 127.0, 500, PriceSort.PRICE).orElseThrow();

		assertThat(response.baseDate()).isEqualTo(new PriceCompareResponse.BaseDates(LocalDate.of(2026, 10, 2), null));
		assertThat(response.item()).isNull();
		assertThat(response.hospitals()).isEmpty();
		assertThat(response.reference()).isNull();
		assertThat(service.searchItems("MRI").items()).isEmpty();
		assertThat(service.featuredItems().items()).isEmpty();
		assertThat(service.activeBaseDate()).isEmpty();
	}

	@Test
	void 시험_실행_스냅샷의_가격은_읽지_않는다() {
		long trial = insertSnapshot("NONPAY", "TRIAL", "2026-10-04");
		insertItem(trial, MRI, "자기공명영상진단료(MRI-기본검사)/척추-요천추/일반");
		insertPrice(trial, MRI, "Y-A", 1, 1, "360000");

		assertThat(compare(500, PriceSort.PRICE).hospitals()).extracting(PricedHospital::minPrice)
				.doesNotContain(1L);
	}

	@Test
	void 항목_검색은_짧은_이름과_공개_병원_수를_준다() {
		NonpayItemResponse item = service.searchItems(" 척추 ").items().getFirst();

		assertThat(item.code()).isEqualTo(MRI);
		assertThat(item.detail()).isEqualTo("척추-요천추/일반");
		assertThat(item.hospitalCount()).isEqualTo(3);
		assertThat(item.label()).isNull();
		assertThat(service.searchItems("척추").baseDate()).isEqualTo(LocalDate.of(2026, 10, 3));
	}

	@Test
	void 빠른_선택은_설정_순서이고_가격이_없는_코드는_뺀다() {
		insertItem(nonpayActive, "ABZ010001", "상급병실료/1인실");
		insertPrice(nonpayActive, "ABZ010001", "Y-A", 200000, 200000, "360000");
		insertItem(nonpayActive, "MX1220000", "이학요법료/도수치료"); // 가격 없음

		assertThat(service.featuredItems().items()).extracting(NonpayItemResponse::code, NonpayItemResponse::label)
				.containsExactly(tuple("ABZ010001", "1인실"), tuple(MRI, "허리 MRI"));
	}

	@Test
	void 비급여_ACTIVE_기준일을_준다() {
		assertThat(service.activeBaseDate()).contains(LocalDate.of(2026, 10, 3));
	}

	private PriceCompareResponse compare(int radius, PriceSort sort) {
		return service.comparePrices(MRI, 37.5, 127.0, radius, sort).orElseThrow();
	}

	private long insertSnapshot(String source, String status, String baseDate) {
		jdbcTemplate.update("insert into snapshot (source, status, base_date, record_count) values (?, ?, ?, 0)",
				source, status, baseDate);
		return jdbcTemplate.queryForObject("select id from snapshot where source = ? and status = ?", Long.class,
				source, status);
	}

	private void insertHospital(long snapshotId, String ykiho, String name, double lat, double lng) {
		jdbcTemplate.update("""
				insert into hospital (snapshot_id, ykiho, name, cl_cd, cl_cd_nm, latitude, longitude)
				values (?, ?, ?, '21', '병원', ?, ?)
				""", snapshotId, ykiho, name, lat, lng);
	}

	private void insertItem(long snapshotId, String code, String name) {
		jdbcTemplate.update("insert into nonpay_item (snapshot_id, npay_cd, npay_kor_nm, mdiv_cd_nm) values (?, ?, ?, ?)",
				snapshotId, code, name, name.substring(0, name.indexOf('/')));
	}

	private void insertPrice(long snapshotId, String code, String ykiho, long min, long max, String sidoCd) {
		jdbcTemplate.update("""
				insert into nonpay_price (snapshot_id, npay_cd, ykiho, min_price, max_price, sido_cd)
				values (?, ?, ?, ?, ?, ?)
				""", snapshotId, code, ykiho, min, max, sidoCd);
	}

	private void insertStat(long snapshotId, String code, String dimension, String key, long median) {
		jdbcTemplate.update("""
				insert into nonpay_stat (snapshot_id, npay_cd, dimension, dim_key, min_price, max_price, avg_price,
				                         median_price, std_date)
				values (?, ?, ?, ?, 1, 2000000, ?, ?, '2026-09-03')
				""", snapshotId, code, dimension, key, median, median);
	}

}
