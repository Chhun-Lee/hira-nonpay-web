package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.hira.dto.NonpayHospPrice;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayPriceRecord;
import dev.chhun.hospitalcompare.snapshot.dto.QualityIssue;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 병원 가격 행을 적재 전에 검증한다. 필수값이 없거나 가격이 이상하면 행을 빼고(비교 정렬이 깨지므로),
 * 적용일만 틀리면 날짜를 비우고 적재한다. 뺀 행은 게이트가 비율로 본다.
 */
public class NonpayPriceValidator {

	public Result validate(String requestedItemCd, List<NonpayHospPrice> rows) {
		List<NonpayPriceRecord> records = new ArrayList<>();
		List<QualityIssue> issues = new ArrayList<>();
		int excluded = 0;
		for (NonpayHospPrice row : rows) {
			String ykiho = NonpayValues.isBlank(row.ykiho()) ? null : row.ykiho();
			if (ykiho == null || NonpayValues.isBlank(row.npayCd())
					|| NonpayValues.isBlank(row.minPrc()) || NonpayValues.isBlank(row.maxPrc())) {
				issues.add(new QualityIssue(ykiho, QualityIssueType.MISSING_REQUIRED, "itemCd=" + requestedItemCd
						+ ", npayCd=" + row.npayCd() + ", minPrc=" + row.minPrc() + ", maxPrc=" + row.maxPrc()));
				excluded++;
				continue;
			}
			if (!requestedItemCd.equals(row.npayCd())) {
				issues.add(new QualityIssue(ykiho, QualityIssueType.MISSING_REQUIRED,
						"요청 itemCd=" + requestedItemCd + ", 응답 npayCd=" + row.npayCd()));
				excluded++;
				continue;
			}
			Long min = NonpayValues.amount(row.minPrc());
			Long max = NonpayValues.amount(row.maxPrc());
			if (min == null || max == null || min <= 0 || max <= 0 || min > max) {
				issues.add(new QualityIssue(ykiho, QualityIssueType.INVALID_PRICE,
						"npayCd=" + row.npayCd() + ", minPrc=" + row.minPrc() + ", maxPrc=" + row.maxPrc()));
				excluded++;
				continue;
			}
			LocalDate adtFrDd = null;
			if (NonpayValues.isValidDate(row.adtFrDd())) {
				adtFrDd = NonpayValues.date(row.adtFrDd());
			} else {
				issues.add(new QualityIssue(ykiho, QualityIssueType.INVALID_DATE,
						"npayCd=" + row.npayCd() + ", adtFrDd=" + row.adtFrDd()));
			}
			records.add(new NonpayPriceRecord(row.npayCd(), ykiho, min, max, row.clCd(), row.sidoCd(),
					row.sgguCd(), adtFrDd));
		}
		return new Result(List.copyOf(records), List.copyOf(issues), excluded);
	}

	/**
	 * @param excluded 적재하지 않은 행 수(필수값 누락, 다른 항목, 가격 이상)
	 */
	public record Result(List<NonpayPriceRecord> records, List<QualityIssue> issues, int excluded) {
	}

}
