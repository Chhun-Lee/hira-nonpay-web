package dev.chhun.hospitalcompare.nonpay.service;

import dev.chhun.hospitalcompare.detail.dto.OpenNowSummary;
import dev.chhun.hospitalcompare.detail.dto.OpenState;
import dev.chhun.hospitalcompare.detail.dto.OpenStatus;
import dev.chhun.hospitalcompare.detail.service.OpenStatusService;
import dev.chhun.hospitalcompare.hospital.service.HospitalQueryService;
import dev.chhun.hospitalcompare.nonpay.config.NonpayUiProperties;
import dev.chhun.hospitalcompare.nonpay.dto.NearbyPrice;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayItemResponse;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayItemSearchResponse;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayItemSummary;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayStatRecord;
import dev.chhun.hospitalcompare.nonpay.dto.PriceCompareResponse;
import dev.chhun.hospitalcompare.nonpay.dto.PricedHospital;
import dev.chhun.hospitalcompare.nonpay.dto.ReferencePrices;
import dev.chhun.hospitalcompare.nonpay.dto.StatValues;
import dev.chhun.hospitalcompare.nonpay.entity.StatDimension;
import dev.chhun.hospitalcompare.nonpay.repository.NonpayQueryRepository;
import dev.chhun.hospitalcompare.snapshot.entity.Snapshot;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotSource;
import dev.chhun.hospitalcompare.snapshot.entity.SnapshotStatus;
import dev.chhun.hospitalcompare.snapshot.repository.SnapshotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비급여 항목 검색과 항목별 가격 비교. 비급여 ACTIVE와 병원 목록 ACTIVE를 한 읽기 트랜잭션에서 읽어 같은 시점을 본다.
 * 사용자 좌표는 로그에 남기지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class NonpayQueryService {

	private static final Logger log = LoggerFactory.getLogger(NonpayQueryService.class);

	/** 항목 검색 결과 최대 개수 */
	static final int SEARCH_LIMIT = 20;

	/** 종별 기준값 순서. All(전체)은 전국 기준값으로 따로 쓴다 */
	private static final List<String> TYPE_ORDER = List.of("Usgh", "Gnhp", "Hosp", "Recu", "Dety", "Cmdc");

	/** 우리 시도를 정할 병원: 지도 중심에 가장 가까운 병원 */
	private static final Comparator<NearbyPrice> NEAREST = Comparator.comparingInt(NearbyPrice::distanceMeters)
			.thenComparing(NearbyPrice::ykiho);

	private final SnapshotRepository snapshotRepository;
	private final NonpayQueryRepository nonpayQueryRepository;
	private final NonpayUiProperties properties;
	private final OpenStatusService openStatusService;

	public NonpayQueryService(SnapshotRepository snapshotRepository, NonpayQueryRepository nonpayQueryRepository,
			NonpayUiProperties properties, OpenStatusService openStatusService) {
		this.snapshotRepository = snapshotRepository;
		this.nonpayQueryRepository = nonpayQueryRepository;
		this.properties = properties;
		this.openStatusService = openStatusService;
	}

	/** 비급여 ACTIVE 기준일. 비급여 ACTIVE가 없으면 empty */
	public Optional<LocalDate> activeBaseDate() {
		return active(SnapshotSource.NONPAY).map(Snapshot::getBaseDate);
	}

	public NonpayItemSearchResponse searchItems(String query) {
		return active(SnapshotSource.NONPAY)
				.map(snapshot -> new NonpayItemSearchResponse(snapshot.getBaseDate(),
						nonpayQueryRepository.searchItems(snapshot.getId(), query.strip(), SEARCH_LIMIT).stream()
								.map(item -> NonpayItemResponse.of(item, null))
								.toList()))
				.orElseGet(() -> new NonpayItemSearchResponse(null, List.of()));
	}

	/** 설정 순서대로. 비급여 ACTIVE에 없거나 가격을 공개한 병원이 없는 코드는 뺀다. */
	public NonpayItemSearchResponse featuredItems() {
		Optional<Snapshot> nonpay = active(SnapshotSource.NONPAY);
		if (nonpay.isEmpty()) {
			return new NonpayItemSearchResponse(null, List.of());
		}
		List<NonpayUiProperties.FeaturedItem> featured = properties.featuredItems();
		Map<String, NonpayItemSummary> found = nonpayQueryRepository
				.findItems(nonpay.get().getId(), featured.stream().map(NonpayUiProperties.FeaturedItem::code).toList())
				.stream()
				.collect(Collectors.toMap(NonpayItemSummary::code, Function.identity()));
		return new NonpayItemSearchResponse(nonpay.get().getBaseDate(), featured.stream()
				.filter(item -> found.containsKey(item.code()))
				.map(item -> NonpayItemResponse.of(found.get(item.code()), item.label()))
				.toList());
	}

	public Optional<PriceCompareResponse> comparePrices(String itemCd, double latitude, double longitude,
			int radiusMeters, PriceSort sort) {
		return comparePrices(itemCd, latitude, longitude, radiusMeters, sort, false);
	}

	/**
	 * 반경 안에서 이 항목 가격을 공개한 병원과 기준값. 반경 기준값과 total은 500곳으로 자르기 전, 지금 진료 중 필터 전 전체로 센다.
	 *
	 * @param openNow true면 지금 진료 중인 병원만 남긴다(자르기 전에 거른다)
	 * @return 모르는 항목 코드면 empty. 비급여 ACTIVE가 없으면 빈 결과
	 */
	public Optional<PriceCompareResponse> comparePrices(String itemCd, double latitude, double longitude,
			int radiusMeters, PriceSort sort, boolean openNow) {
		LocalDateTime at = openStatusService.now();
		Optional<Snapshot> hospitals = active(SnapshotSource.HOSPITAL_LIST);
		LocalDate hospitalBaseDate = hospitals.map(Snapshot::getBaseDate).orElse(null);
		Optional<Snapshot> nonpay = active(SnapshotSource.NONPAY);
		if (nonpay.isEmpty()) {
			return Optional.of(new PriceCompareResponse(new PriceCompareResponse.BaseDates(hospitalBaseDate, null),
					null, 0, List.of(), null, null, openNow ? new OpenNowSummary(at, 0, 0) : null));
		}
		long nonpayId = nonpay.get().getId();
		Optional<NonpayItemSummary> item = nonpayQueryRepository.findItem(nonpayId, itemCd);
		if (item.isEmpty()) {
			return Optional.empty();
		}
		List<NearbyPrice> nearby = hospitals
				.map(snapshot -> nonpayQueryRepository.findNearbyPrices(nonpayId, snapshot.getId(), itemCd, latitude,
						longitude, radiusMeters))
				.orElseGet(List::of);
		Map<String, OpenStatus> statuses = openStatusService.statuses(
				nearby.stream().map(NearbyPrice::ykiho).toList(), at);
		Stream<NearbyPrice> shown = nearby.stream();
		OpenNowSummary summary = null;
		if (openNow) {
			List<NearbyPrice> open = nearby.stream()
					.filter(price -> statuses.get(price.ykiho()).status() == OpenState.OPEN)
					.toList();
			int unknown = (int) nearby.stream()
					.filter(price -> statuses.get(price.ykiho()).status() == OpenState.UNKNOWN)
					.count();
			summary = new OpenNowSummary(at, open.size(), unknown);
			shown = open.stream();
		}
		String sidoCd = nearby.stream().min(NEAREST).map(NearbyPrice::sidoCd).orElse(null);
		return Optional.of(new PriceCompareResponse(
				new PriceCompareResponse.BaseDates(hospitalBaseDate, nonpay.get().getBaseDate()),
				NonpayItemResponse.of(item.get(), null),
				nearby.size(),
				shown.sorted(sort.order())
						.limit(HospitalQueryService.MAX_RESULTS)
						.map(price -> PricedHospital.of(price, statuses.get(price.ykiho())))
						.toList(),
				PriceStats.of(nearby, NearbyPrice::minPrice, NearbyPrice::maxPrice).orElse(null),
				reference(nonpayQueryRepository.findStats(nonpayId, itemCd), sidoCd),
				summary));
	}

	private static ReferencePrices reference(List<NonpayStatRecord> stats, String sidoCd) {
		if (stats.isEmpty()) {
			return null;
		}
		Map<String, NonpayStatRecord> byType = byKey(stats, StatDimension.CL_TYPE);
		Map<String, NonpayStatRecord> bySido = byKey(stats, StatDimension.SIDO);
		LocalDate stdDate = stats.stream().map(NonpayStatRecord::stdDate).filter(Objects::nonNull).findFirst()
				.orElse(null);
		NonpayStatRecord all = byType.get("All");
		return new ReferencePrices(
				stdDate,
				all == null ? null : StatValues.of(all, "전국"),
				sido(bySido, sidoCd),
				TYPE_ORDER.stream()
						.filter(byType::containsKey)
						.map(key -> StatValues.of(byType.get(key), StatDimension.CL_TYPE.displayName(key)))
						.toList());
	}

	private static StatValues sido(Map<String, NonpayStatRecord> bySido, String sidoCd) {
		if (sidoCd == null) {
			return null;
		}
		String suffix = SidoSuffix.of(sidoCd);
		if (suffix == null) {
			log.warn("시도 코드 {}의 통계 접미사를 모릅니다. 시도 기준값을 비웁니다", sidoCd);
			return null;
		}
		NonpayStatRecord stat = bySido.get(suffix);
		return stat == null ? null : StatValues.of(stat, StatDimension.SIDO.displayName(suffix));
	}

	private static Map<String, NonpayStatRecord> byKey(List<NonpayStatRecord> stats, StatDimension dimension) {
		return stats.stream()
				.filter(stat -> stat.dimension() == dimension)
				.collect(Collectors.toMap(NonpayStatRecord::dimKey, Function.identity(), (first, second) -> first));
	}

	private Optional<Snapshot> active(SnapshotSource source) {
		return snapshotRepository.findBySourceAndStatus(source, SnapshotStatus.ACTIVE);
	}

}
