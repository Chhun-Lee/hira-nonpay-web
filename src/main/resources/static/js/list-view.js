// 왼쪽 목록. 병원 데이터는 textContent로만 넣는다(이름·주소에 특수문자가 있어도 글자 그대로 보이게).
import { prefersReducedMotion } from './motion.js';

const EMPTY_MESSAGE = '반경 안에 병원이 없어요. 반경을 넓히거나 지도를 옮겨 보세요.';

function formatDistance(meters) {
  return meters < 1000 ? `${meters}m` : `${(meters / 1000).toFixed(1)}km`;
}

function formatRadius(meters) {
  return meters < 1000 ? `${meters}m` : `${meters / 1000}km`;
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) {
    node.className = className;
  }
  if (text !== undefined && text !== null) {
    node.textContent = text;
  }
  return node;
}

export function createListView({ summary, notice, rows, onSelect }) {
  const items = new Map(); // ykiho → li

  function setNotice(message) {
    notice.textContent = message ?? '';
    notice.hidden = !message;
  }

  function clearRows() {
    rows.replaceChildren();
    items.clear();
  }

  function renderInfo(hospital) {
    const info = element('span', 'row-info');
    const sub = element('span', 'row-sub');
    if (hospital.clCdNm) {
      sub.append(element('span', null, hospital.clCdNm));
    }
    if (hospital.doctorCount !== null && hospital.doctorCount !== undefined) {
      sub.append(element('span', null, `의사 ${hospital.doctorCount}명`));
    }
    info.append(element('strong', 'row-name', hospital.name), sub);
    if (hospital.address) {
      info.append(element('span', 'row-address', hospital.address));
    }
    return info;
  }

  function renderRow(hospital) {
    const item = element('li', 'row-item');
    const button = element('button', 'row');
    button.type = 'button';
    button.setAttribute('aria-pressed', 'false');
    const seal = element('span', 'row-seal', '선택');
    seal.setAttribute('aria-hidden', 'true');
    button.append(
      element('span', 'row-code', hospital.clCd ?? '–'),
      renderInfo(hospital),
      element('span', 'row-dist', formatDistance(hospital.distanceMeters)),
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

    render({ hospitals, radius, maxResults }) {
      summary.textContent = `반경 ${formatRadius(radius)} 안 ${hospitals.length}곳`;
      if (hospitals.length === 0) {
        setNotice(EMPTY_MESSAGE);
      } else if (hospitals.length >= maxResults) {
        setNotice(`가까운 ${maxResults}곳만 보여요. 반경을 줄이면 빠짐없이 볼 수 있어요.`);
      } else {
        setNotice(null);
      }
      clearRows();
      for (const hospital of hospitals) {
        const item = renderRow(hospital);
        items.set(hospital.ykiho, item);
        rows.append(item);
      }
      rows.scrollTop = 0;
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
