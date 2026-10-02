package dev.chhun.hospitalcompare.hospital;

import java.time.LocalDate;

public record SnapshotResponse(LocalDate baseDate, int recordCount) {
}
