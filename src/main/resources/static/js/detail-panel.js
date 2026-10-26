// 상세 패널(F4). 목록 칸 자리에 병원 하나의 진료시간·진료과목·비급여 가격을 보여 준다.
// 서버 데이터는 textContent로만 넣는다(이름·주소·원문에 특수문자가 있어도 글자 그대로 보이게).
import { element } from './dom.js';
import { dayOfWeek, formatPrice, statusLabel } from './format.js';

const DAY_NAMES = {
  MONDAY: '월', TUESDAY: '화', WEDNESDAY: '수', THURSDAY: '목', FRIDAY: '금', SATURDAY: '토', SUNDAY: '일',
};
const NO_HOURS = '심평원에 진료시간이 등록되지 않았어요. 방문 전 전화로 확인하세요.';
const CLINIC_NO_DETAIL = '의원급 상세정보는 아직 수집 전이에요.';
const NO_SUBJECTS = '진료과목 정보가 없어요.';
const NO_NONPAY = '이 병원이 공개한 비급여 가격이 없어요.';
const CLINIC_NONPAY = '의원급 가격은 공개 범위 밖이에요(Open API는 병원급 이상만 제공). ';

// onChooseItem({ code, label }): 비급여 항목을 누르면 부른다(가격 비교 모드로 들어간다)
export function createDetailPanel({ body, hiraNonpayUrl, onChooseItem }) {
  function section(title, ...children) {
    const node = element('section', 'detail-section');
    node.append(element('h3', 'detail-title', title), ...children);
    return node;
  }

  function line(label, value) {
    const node = element('div', 'detail-line');
    node.append(element('span', 'detail-label', label), element('span', null, value));
    return node;
  }

  function note(text) {
    return element('p', 'detail-note', text);
  }

  function renderHero(hospital) {
    const hero = element('div', 'detail-hero');
    const name = element('h2', 'detail-name', hospital.name);
    name.id = 'detail-name';
    hero.append(name);
    const label = statusLabel(hospital.openStatus);
    if (label) {
      const { status, reason } = hospital.openStatus;
      hero.append(element('span', `badge badge-${status.toLowerCase()}`,
        status === 'UNKNOWN' && reason ? `${label} · ${reason}` : label));
    }
    const sub = element('div', 'detail-sub');
    if (hospital.clCdNm) {
      sub.append(element('span', null, hospital.clCdNm));
    }
    if (hospital.doctorCount !== null && hospital.doctorCount !== undefined) {
      sub.append(element('span', null, `의사 ${hospital.doctorCount}명`));
    }
    if (hospital.phone) {
      const tel = element('a', null, hospital.phone);
      tel.href = `tel:${hospital.phone}`;
      sub.append(tel);
    }
    hero.append(sub);
    if (hospital.address) {
      hero.append(element('p', 'detail-address', hospital.address));
    }
    return hero;
  }

  function dayText(day) {
    if (day.state === 'OPEN_DAY') {
      return `${day.open}–${day.close}`;
    }
    return day.state === 'UNKNOWN_DAY' ? '확인 필요' : '휴진';
  }

  // 원문 그대로 보여 주는 안내(점심·접수·휴진). 없는 줄은 뺀다.
  function originals(detail) {
    return [
      ['점심(평일)', detail.lunchWeek?.text],
      ['점심(토)', detail.lunchSat?.text],
      ['접수(평일)', detail.receptionWeek],
      ['접수(토)', detail.receptionSat],
      ['일요일', detail.sundayNote?.text],
      ['공휴일', detail.holidayNote?.text],
    ].filter(([, value]) => value).map(([label, value]) => line(label, value));
  }

  function renderHours(hospital) {
    if (!hospital.hospitalLevel) {
      return section('진료시간', note(CLINIC_NO_DETAIL));
    }
    const detail = hospital.detail;
    if (!detail || detail.hours.length === 0) {
      return section('진료시간', note(NO_HOURS), ...(detail ? originals(detail) : []));
    }
    const today = hospital.judgedAt ? dayOfWeek(hospital.judgedAt) : null;
    const days = element('ul', 'detail-days');
    for (const day of detail.hours) {
      const item = element('li', day.day === today ? 'is-today' : null);
      item.append(element('span', 'detail-day', DAY_NAMES[day.day]), element('span', null, dayText(day)));
      days.append(item);
    }
    return section('진료시간', days, ...originals(detail));
  }

  function yesNo(value) {
    if (value === true) {
      return '운영';
    }
    return value === false ? '없음' : '정보 없음';
  }

  function renderFacilities(detail) {
    const lines = [line('응급실', `주간 ${yesNo(detail.emergencyDay)} · 야간 ${yesNo(detail.emergencyNight)}`)];
    const { capacity, fee, note: parkingNote } = detail.parking;
    const parts = [];
    if (capacity !== null && capacity !== undefined) {
      parts.push(`${capacity}대`);
    }
    if (fee === true || fee === false) {
      parts.push(fee ? '주차비 부담 있음' : '주차비 부담 없음');
    }
    if (parts.length > 0) {
      lines.push(line('주차', parts.join(' · ')));
    }
    if (parkingNote) {
      lines.push(line('주차 안내', parkingNote));
    }
    return section('응급실·주차', ...lines);
  }

  function renderSubjects(subjects) {
    if (subjects.length === 0) {
      return section('진료과목 · 전문의 수', note(NO_SUBJECTS));
    }
    const chips = element('ul', 'chips');
    for (const subject of subjects) {
      const chip = element('li', 'chip', subject.name);
      if (subject.specialists !== null && subject.specialists !== undefined) {
        chip.append(element('em', null, String(subject.specialists)));
      }
      chips.append(chip);
    }
    return section('진료과목 · 전문의 수', chips);
  }

  function renderNonpay(hospital) {
    if (!hospital.hospitalLevel) {
      const text = note(CLINIC_NONPAY);
      const link = element('a', null, '심평원 누리집에서 확인 →');
      link.href = hiraNonpayUrl;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      text.append(link);
      return section('비급여 가격', text);
    }
    const items = hospital.nonpayItems;
    if (items.length === 0) {
      return section('비급여 가격', note(NO_NONPAY));
    }
    const groups = new Map(); // 중분류 → 항목. 서버 정렬(중분류 → 이름) 순서를 그대로 쓴다
    for (const item of items) {
      const category = item.category ?? '분류 없음';
      if (!groups.has(category)) {
        groups.set(category, []);
      }
      groups.get(category).push(item);
    }
    const children = [note(`공개 항목 ${items.length}개 · 항목을 누르면 근처 병원과 가격을 비교해요`)];
    for (const [category, groupItems] of groups) {
      const group = element('details', 'nonpay-group');
      group.open = groups.size === 1;
      group.append(element('summary', null, `${category} (${groupItems.length})`));
      const list = element('ul', 'nonpay-items');
      for (const item of groupItems) {
        const label = item.detail ?? item.code;
        const button = element('button', 'nonpay-item');
        button.type = 'button';
        button.title = item.name ?? label;
        button.append(element('span', null, label), element('span', 'nonpay-price', formatPrice(item.minPrice, item.maxPrice)));
        button.addEventListener('click', () => onChooseItem({ code: item.code, label }));
        const row = element('li');
        row.append(button);
        list.append(row);
      }
      group.append(list);
      children.push(group);
    }
    return section('비급여 가격', ...children);
  }

  function renderBaseDates(baseDates) {
    return element('p', 'detail-base',
      `기준일 · 상세 ${baseDates.detail ?? '수집 전'} · 비급여 ${baseDates.nonpay ?? '수집 전'}`);
  }

  return {
    showLoading() {
      body.replaceChildren(element('p', 'detail-message', '불러오는 중'));
    },

    showError(message) {
      body.replaceChildren(element('p', 'detail-message', message));
    },

    render(hospital) {
      const parts = [renderHero(hospital), renderHours(hospital)];
      if (hospital.detail) {
        parts.push(renderFacilities(hospital.detail));
      }
      if (hospital.hospitalLevel) {
        parts.push(renderSubjects(hospital.subjects));
      }
      parts.push(renderNonpay(hospital), renderBaseDates(hospital.baseDates));
      body.replaceChildren(...parts);
      body.scrollTop = 0;
    },
  };
}
