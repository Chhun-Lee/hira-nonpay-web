package dev.chhun.hospitalcompare.collector.service;

import dev.chhun.hospitalcompare.nonpay.dto.NonpayStatRecord;
import dev.chhun.hospitalcompare.nonpay.entity.StatDimension;
import dev.chhun.hospitalcompare.snapshot.dto.QualityIssue;
import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 가로형 통계(prcMinSl, prcAvgUsgh …)를 (구분, 접미사) 세로형 행으로 펼친다.
 * 네 값이 모두 0이거나 없는 칸은 버리고, 모르는 접미사와 숫자가 아닌 값은 품질 이슈로 남긴다.
 */
public class NonpayStatFlattener {

	private static final Pattern VALUE_KEY = Pattern.compile("^(prcMin|prcMax|prcAvg|middAvg)(.+)$");

	public Result flatten(StatDimension dimension, List<Map<String, String>> rows) {
		List<NonpayStatRecord> records = new ArrayList<>();
		List<QualityIssue> issues = new ArrayList<>();
		Set<String> npayCds = new LinkedHashSet<>();
		for (Map<String, String> row : rows) {
			String npayCd = row.get("npayCd");
			if (NonpayValues.isBlank(npayCd)) {
				issues.add(new QualityIssue(null, QualityIssueType.MISSING_REQUIRED,
						"통계 " + dimension + " npayCd 없음: " + new TreeSet<>(row.keySet())));
				continue;
			}
			npayCds.add(npayCd);
			LocalDate stdDate = null;
			if (NonpayValues.isValidDate(row.get("stdDate"))) {
				stdDate = NonpayValues.date(row.get("stdDate"));
			} else {
				issues.add(new QualityIssue(null, QualityIssueType.INVALID_DATE,
						"통계 " + dimension + " npayCd=" + npayCd + ", stdDate=" + row.get("stdDate")));
			}
			final LocalDate finalStdDate = stdDate;
			Map<String, Long[]> byKey = new TreeMap<>();
			Set<String> unknownKeys = new TreeSet<>();
			for (Map.Entry<String, String> entry : row.entrySet()) {
				Matcher matcher = VALUE_KEY.matcher(entry.getKey());
				if (!matcher.matches()) {
					continue;
				}
				String key = matcher.group(2);
				if (!dimension.knows(key)) {
					unknownKeys.add(key);
					continue;
				}
				Long amount = NonpayValues.amount(entry.getValue());
				if (amount == null && !NonpayValues.isBlank(entry.getValue())) {
					issues.add(new QualityIssue(null, QualityIssueType.INVALID_PRICE,
							"통계 " + dimension + " npayCd=" + npayCd + ", " + entry.getKey() + "=" + entry.getValue()));
				}
				byKey.computeIfAbsent(key, k -> new Long[4])[index(matcher.group(1))] = amount;
			}
			for (String key : unknownKeys) {
				issues.add(new QualityIssue(null, QualityIssueType.UNKNOWN_STAT_KEY,
						"통계 " + dimension + " npayCd=" + npayCd + ", 접미사=" + key));
			}
			byKey.forEach((key, values) -> {
				if (hasValue(values)) {
					records.add(new NonpayStatRecord(npayCd, dimension, key, values[0], values[1], values[2],
							values[3], finalStdDate));
				}
			});
		}
		return new Result(List.copyOf(records), List.copyOf(issues), List.copyOf(npayCds));
	}

	private static int index(String prefix) {
		return switch (prefix) {
			case "prcMin" -> 0;
			case "prcMax" -> 1;
			case "prcAvg" -> 2;
			default -> 3; // middAvg(중간값)
		};
	}

	private static boolean hasValue(Long[] values) {
		for (Long value : values) {
			if (value != null && value != 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param npayCds 이 응답에 나온 항목 코드(응답 순서). 종별 통계의 것이 수집 대상 항목이 된다
	 */
	public record Result(List<NonpayStatRecord> records, List<QualityIssue> issues, List<String> npayCds) {
	}

}
