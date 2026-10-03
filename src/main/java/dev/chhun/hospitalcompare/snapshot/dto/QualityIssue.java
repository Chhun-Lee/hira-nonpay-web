package dev.chhun.hospitalcompare.snapshot.dto;

import dev.chhun.hospitalcompare.snapshot.entity.QualityIssueType;

/**
 * 품질 이슈 한 건의 값. snapshot_id는 저장할 때 따로 받는다.
 */
public record QualityIssue(String ykiho, QualityIssueType type, String rawValue) {
}
