// 조회 API 호출. 지도 중심 좌표는 요청에만 쓰고 어디에도 남기지 않는다(로그 금지).

export async function fetchRegions() {
  const response = await fetch('/api/regions', { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`지역 목록 HTTP ${response.status}`);
  }
  return response.json();
}

export async function searchHospitals({ latitude, longitude, radius, clCd }) {
  const params = new URLSearchParams({ lat: String(latitude), lng: String(longitude), radius: String(radius) });
  if (clCd) {
    params.set('clCd', clCd);
  }
  const response = await fetch(`/api/hospitals?${params}`, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`병원 검색 HTTP ${response.status}`);
  }
  return response.json();
}
