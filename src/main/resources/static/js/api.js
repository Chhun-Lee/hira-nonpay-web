// 조회 API 호출. 지도 중심 좌표는 요청에만 쓰고 어디에도 남기지 않는다(로그 금지).

async function getJson(path, params, label) {
  const url = params ? `${path}?${params}` : path;
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`${label} HTTP ${response.status}`);
  }
  return response.json();
}

export function fetchRegions() {
  return getJson('/api/regions', null, '지역 목록');
}

export function searchHospitals({ latitude, longitude, radius, clCd }) {
  const params = new URLSearchParams({ lat: String(latitude), lng: String(longitude), radius: String(radius) });
  if (clCd) {
    params.set('clCd', clCd);
  }
  return getJson('/api/hospitals', params, '병원 검색');
}

export function fetchFeaturedItems() {
  return getJson('/api/noncovered/items/featured', null, '빠른 선택 항목');
}

export function searchItems(query) {
  return getJson('/api/noncovered/items', new URLSearchParams({ q: query }), '항목 검색');
}

export function comparePrices({ itemCd, latitude, longitude, radius, sort }) {
  const params = new URLSearchParams({
    itemCd,
    lat: String(latitude),
    lng: String(longitude),
    radius: String(radius),
    sort,
  });
  return getJson('/api/noncovered/prices', params, '가격 비교');
}
