package dev.chhun.hospitalcompare.hira.dto;

/** {@code <response><header>}. 오류여도 온다. 정상은 resultCode 00이다. */
public record HiraHeader(String resultCode, String resultMsg) {
}
