package dev.chhun.hospitalcompare.nonpay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.chhun.hospitalcompare.TestcontainersConfiguration;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayItemRecord;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayPriceRecord;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayStatRecord;
import dev.chhun.hospitalcompare.nonpay.entity.StatDimension;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import({TestcontainersConfiguration.class, NonpayItemRepository.class, NonpayPriceRepository.class,
		NonpayStatRepository.class})
class NonpayRepositoryTest {

	private static final LocalDate DAY = LocalDate.of(2026, 9, 3);

	@Autowired
	NonpayItemRepository itemRepository;

	@Autowired
	NonpayPriceRepository priceRepository;

	@Autowired
	NonpayStatRepository statRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 가격은_같은_항목과_병원을_다시_넣어도_한_행이고_값은_새것이다() {
		priceRepository.upsertAll(1L, List.of(price("ABZ010001", "Y-A", 200000, 300000)));
		priceRepository.upsertAll(1L, List.of(price("ABZ010001", "Y-A", 210000, 310000)));

		assertThat(priceRepository.countBySnapshot(1L)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select min_price from nonpay_price where snapshot_id = 1 and npay_cd = 'ABZ010001' and ykiho = 'Y-A'",
				Long.class)).isEqualTo(210000L);
	}

	@Test
	void 같은_병원이라도_스냅샷이나_항목이_다르면_다른_행이다() {
		priceRepository.upsertAll(1L, List.of(price("ABZ010001", "Y-A", 1, 1), price("HE1180000", "Y-A", 1, 1)));
		priceRepository.upsertAll(2L, List.of(price("ABZ010001", "Y-A", 1, 1)));

		assertThat(priceRepository.countBySnapshot(1L)).isEqualTo(2);
		assertThat(priceRepository.countBySnapshot(2L)).isEqualTo(1);
	}

	@Test
	void 병원_목록에_없는_ykiho를_센다() {
		jdbcTemplate.update("insert into hospital (snapshot_id, ykiho, name) values (10, 'Y-A', '병원A')");
		jdbcTemplate.update("insert into hospital (snapshot_id, ykiho, name) values (11, 'Y-B', '다른 스냅샷의 병원B')");
		priceRepository.upsertAll(1L, List.of(price("ABZ010001", "Y-A", 1, 1), price("ABZ010001", "Y-B", 1, 1),
				price("HE1180000", "Y-B", 1, 1), price("ABZ010001", "Y-C", 1, 1)));

		assertThat(priceRepository.countUnmatchedYkiho(1L, 10L)).isEqualTo(2);
	}

	@Test
	void 항목과_통계를_upsert한다() {
		NonpayItemRecord item = new NonpayItemRecord("ABZ010001", "상급병실료/1인실", "1010A", "상급병실료",
				"1010A010", "1인실", "1010A010", "1인실", LocalDate.of(2018, 4, 2), LocalDate.of(9999, 12, 31));
		itemRepository.upsertAll(1L, List.of(item));
		itemRepository.upsertAll(1L, List.of(item));
		NonpayStatRecord stat = new NonpayStatRecord("ABZ010001", StatDimension.SIDO, "Sl", 150000L, 540000L,
				260000L, 250000L, DAY);
		statRepository.upsertAll(1L, List.of(stat, new NonpayStatRecord("ABZ010001", StatDimension.CL_TYPE, "All",
				20000L, 540000L, null, 200000L, DAY)));

		assertThat(itemRepository.countBySnapshot(1L)).isEqualTo(1);
		assertThat(statRepository.countBySnapshot(1L)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject(
				"select dimension from nonpay_stat where snapshot_id = 1 and dim_key = 'Sl'", String.class))
				.isEqualTo("SIDO");
		assertThat(jdbcTemplate.queryForObject(
				"select adt_end_dd from nonpay_item where snapshot_id = 1", LocalDate.class))
				.isEqualTo(LocalDate.of(9999, 12, 31));
	}

	private static NonpayPriceRecord price(String npayCd, String ykiho, long min, long max) {
		return new NonpayPriceRecord(npayCd, ykiho, min, max, "01", "110000", "110001", DAY);
	}

}
