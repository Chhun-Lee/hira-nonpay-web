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

// 최소와 최대가 같으면 한 값, 다르면 범위로 쓴다. 범위는 "~" 뒤에서 줄바꿈할 수 있게 안 보이는 공백을 둔다.
export function formatPrice(min, max) {
  if (min === null || min === undefined) {
    return '–';
  }
  return min === max || max === null || max === undefined
    ? `${won.format(min)}원`
    : `${won.format(min)}~​${won.format(max)}원`;
}

// 지금 진료 상태 글자. 진료 끝이면 사유(점심시간, 오늘 휴진 등)를 그대로 쓴다. 상태가 없으면(의원급) null
export function statusLabel(openStatus) {
  if (!openStatus) {
    return null;
  }
  if (openStatus.status === 'OPEN') {
    return '진료 중';
  }
  if (openStatus.status === 'CLOSED') {
    return openStatus.reason ?? '진료 끝';
  }
  return '확인 필요';
}

// 서버 판정 시각("2026-10-07T14:05:00", 한국 시간) → "14:05"
export function formatClock(localDateTime) {
  return localDateTime ? localDateTime.slice(11, 16) : '';
}

const DAY_OF_WEEK = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

// 서버 판정 시각의 요일(서버 DayOfWeek 이름). 브라우저 시간대와 무관하게 날짜 부분으로 계산한다
export function dayOfWeek(localDateTime) {
  const [year, month, day] = localDateTime.slice(0, 10).split('-').map(Number);
  return DAY_OF_WEEK[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
}
