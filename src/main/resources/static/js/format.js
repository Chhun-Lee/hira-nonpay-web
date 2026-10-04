// 거리·반경·금액 표시 형식.

const won = new Intl.NumberFormat('ko-KR');

export function formatDistance(meters) {
  return meters < 1000 ? `${meters}m` : `${(meters / 1000).toFixed(1)}km`;
}

export function formatRadius(meters) {
  return meters < 1000 ? `${meters}m` : `${meters / 1000}km`;
}

export function formatWon(value) {
  return value === null || value === undefined ? '–' : `${won.format(value)}원`;
}

// 최소와 최대가 같으면 한 값, 다르면 범위로 쓴다.
export function formatPrice(min, max) {
  if (min === null || min === undefined) {
    return '–';
  }
  return min === max || max === null || max === undefined
    ? `${won.format(min)}원`
    : `${won.format(min)}~${won.format(max)}원`;
}
