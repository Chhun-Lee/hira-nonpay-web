package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.hira.dto.NonpayItemCode;
import dev.chhun.hospitalcompare.nonpay.dto.NonpayItemRecord;
import dev.chhun.hospitalcompare.snapshot.dto.QualityIssue;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 항목 코드를 적재 전에 검증한다. 코드가 없으면 빼고, 긴 이름은 잘라서, 틀린 날짜는 비워서 적재한다.
 * 값 하나 때문에 페이지 전체(다중 행 INSERT)가 실패하지 않게 하려는 것이다.
 */
public class NonpayItemValidator {

	static final int NAME_MAX = 400;

	public Result validate(List<NonpayItemCode> items) {
		List<NonpayItemRecord> records = new ArrayList<>();
		List<QualityIssue> issues = new ArrayList<>();
		for (NonpayItemCode item : items) {
			if (NonpayValues.isBlank(item.npayCd())) {
				issues.add(new QualityIssue(null, QualityIssueType.MISSING_REQUIRED,
						"항목코드 npayCd=" + item.npayCd() + ", npayKorNm=" + item.npayKorNm()));
				continue;
			}
			records.add(new NonpayItemRecord(item.npayCd(),
					name(item, item.npayKorNm(), issues),
					item.npayMdivCd(), name(item, item.npayMdivCdNm(), issues),
					item.npaySdivCd(), name(item, item.npaySdivCdNm(), issues),
					item.npayDtlDivCd(), name(item, item.npayDtlDivCdNm(), issues),
					date(item, "adtFrDd", item.adtFrDd(), issues),
					date(item, "adtEndDd", item.adtEndDd(), issues)));
		}
		return new Result(List.copyOf(records), List.copyOf(issues));
	}

	private static String name(NonpayItemCode item, String value, List<QualityIssue> issues) {
		if (value == null || value.length() <= NAME_MAX) {
			return value;
		}
		issues.add(new QualityIssue(null, QualityIssueType.VALUE_TRUNCATED,
				"항목코드 npayCd=" + item.npayCd() + ", 길이=" + value.length()));
		return value.substring(0, NAME_MAX);
	}

	private static LocalDate date(NonpayItemCode item, String field, String raw, List<QualityIssue> issues) {
		if (!NonpayValues.isValidDate(raw)) {
			issues.add(new QualityIssue(null, QualityIssueType.INVALID_DATE,
					"항목코드 npayCd=" + item.npayCd() + ", " + field + "=" + raw));
			return null;
		}
		return NonpayValues.date(raw);
	}

	public record Result(List<NonpayItemRecord> records, List<QualityIssue> issues) {
	}

}
