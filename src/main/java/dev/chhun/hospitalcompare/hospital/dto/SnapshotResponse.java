package dev.chhun.hospitalcompare.hospital.dto;

import java.time.LocalDate;

public record SnapshotResponse(LocalDate baseDate, int recordCount) {
}
