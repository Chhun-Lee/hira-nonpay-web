// 왼쪽 목록. 병원 데이터는 textContent로만 넣는다(이름·주소에 특수문자가 있어도 글자 그대로 보이게).
import { element } from './dom.js';
import { formatClock, formatDistance, formatPrice, formatRadius, statusLabel } from './format.js';
import { prefersReducedMotion } from './motion.js';

const EMPTY_MESSAGE = '반경 안에 병원이 없어요. 반경을 넓히거나 지도를 옮겨 보세요.';
const EMPTY_PRICE_MESSAGE = '반경 안에 이 항목 가격을 공개한 병원급 의료기관이 없어요. 반경을 넓혀 보세요.';
const EMPTY_OPEN_MESSAGE = '반경 안에 지금 진료 중인 병원급 의료기관이 없어요. 반경을 넓히거나 "지금 진료 중"을 꺼 보세요.';

export function createListView({ summary, notice, rows, onSelect }) {
  const items = new Map(); // ykiho → li

  function setNotice(...messages) {
    const text = messages.filter(Boolean).join(' ');
    notice.textContent = text;
    notice.hidden = !text;
  }

  // 지금 진료 중 필터를 켰을 때의 안내. openNow가 없으면 null
  function openNowNotice(openNow) {
    if (!openNow) {
      return null;
    }
    return `${formatClock(openNow.judgedAt)} 기준 · 진료시간 정보가 없는 병원 ${openNow.unknownCount}곳과 의원급은 제외했어요.`;
  }

  function clearRows() {
    rows.replaceChildren();
    items.clear();
  }

  // 병원 찾기 행: 종별 · 의사 수. 가격 행: 종별 · 거리(오른쪽 칸이 가격이라)
  function renderInfo(hospital, priced) {
    const info = element('span', 'row-info');
    const sub = element('span', 'row-sub');
    if (hospital.clCdNm) {
      sub.append(element('span', null, hospital.clCdNm));
    }
    if (priced) {
      sub.append(element('span', null, formatDistance(hospital.distanceMeters)));
    } else if (hospital.doctorCount !== null && hospital.doctorCount !== undefined) {
      sub.append(element('span', null, `의사 ${hospital.doctorCount}명`));
    }
    const label = statusLabel(hospital.openStatus);
    if (label) {
      sub.append(element('span', `row-status row-status-${hospital.openStatus.status.toLowerCase()}`, label));
    }
    info.append(element('strong', 'row-name', hospital.name), sub);
    if (hospital.address) {
      info.append(element('span', 'row-address', hospital.address));
    }
    return info;
  }

  function renderRow(hospital, priced) {
    const item = element('li', 'row-item');
    const button = element('button', priced ? 'row row-price' : 'row');
    button.type = 'button';
    button.setAttribute('aria-pressed', 'false');
    const seal = element('span', 'row-seal', '선택');
    seal.setAttribute('aria-hidden', 'true');
    button.append(
      element('span', 'row-code', hospital.clCd ?? '–'),
      renderInfo(hospital, priced),
      priced
        ? element('span', 'row-amount', formatPrice(hospital.minPrice, hospital.maxPrice))
        : element('span', 'row-dist', formatDistance(hospital.distanceMeters)),
      seal,
    );
    button.addEventListener('click', () => onSelect(hospital.ykiho));
    item.append(button);
    if (hospital.phone) {
      const tel = element('a', 'row-tel', hospital.phone);
      tel.href = `tel:${hospital.phone}`;
      item.append(tel);
    }
    return item;
  }

  function fill(hospitals, priced) {
    clearRows();
    for (const hospital of hospitals) {
      const item = renderRow(hospital, priced);
      items.set(hospital.ykiho, item);
      rows.append(item);
    }
    rows.scrollTop = 0;
  }

  return {
    showLoading() {
      summary.textContent = '검색 중';
      setNotice(null);
      clearRows();
    },

    showError(message) {
      summary.textContent = '검색하지 못했어요';
      setNotice(message);
      clearRows();
    },

    // notice: 반경을 넓혔다는 알림처럼 앱이 덧붙일 문구(없으면 null). openNow: 지금 진료 중 요약(꺼져 있으면 null)
    render({ hospitals, radius, maxResults, notice, openNow }) {
      const count = openNow ? openNow.openCount : hospitals.length;
      summary.textContent = openNow
        ? `반경 ${formatRadius(radius)} 안 지금 진료 중 ${count}곳`
        : `반경 ${formatRadius(radius)} 안 ${count}곳`;
      if (count === 0) {
        setNotice(notice, openNowNotice(openNow), openNow ? EMPTY_OPEN_MESSAGE : EMPTY_MESSAGE);
      } else if (count > hospitals.length || (!openNow && hospitals.length >= maxResults)) {
        setNotice(notice, openNowNotice(openNow),
          `가까운 ${hospitals.length}곳만 보여요. 반경을 줄이면 빠짐없이 볼 수 있어요.`);
      } else {
        setNotice(notice, openNowNotice(openNow));
      }
      fill(hospitals, false);
    },

    // notice: 반경을 넓혔다는 알림처럼 앱이 덧붙일 문구(없으면 null)
    renderPrices({ hospitals, total, radius, sort, notice, openNow }) {
      const shown = openNow ? openNow.openCount : total;
      summary.textContent = openNow
        ? `반경 ${formatRadius(radius)} 안 ${total}곳 중 지금 진료 중 ${shown}곳`
        : `반경 ${formatRadius(radius)} 안 ${total}곳`;
      if (shown === 0) {
        setNotice(notice, openNowNotice(openNow), openNow && total > 0 ? EMPTY_OPEN_MESSAGE : EMPTY_PRICE_MESSAGE);
      } else if (shown > hospitals.length) {
        const order = sort === 'distance' ? '가까운' : '가격이 낮은';
        setNotice(notice, openNowNotice(openNow),
          `${order} ${hospitals.length}곳만 보여요. 반경을 줄이면 빠짐없이 볼 수 있어요.`);
      } else {
        setNotice(notice, openNowNotice(openNow));
      }
      fill(hospitals, true);
    },

    select(ykiho, { scroll }) {
      for (const [key, item] of items) {
        const selected = key === ykiho;
        item.classList.toggle('is-selected', selected);
        item.querySelector('.row').setAttribute('aria-pressed', String(selected));
      }
      if (scroll) {
        items.get(ykiho)?.scrollIntoView({ block: 'nearest', behavior: prefersReducedMotion() ? 'auto' : 'smooth' });
      }
    },
  };
}
