// 가격 비교 기준값 카드. 반경 안 병원으로 계산한 값과 심평원 통계를 나란히 보여 준다.
import { element } from './dom.js';
import { formatRadius, formatWon } from './format.js';

function range(min, max) {
  if ((min === null || min === undefined) && (max === null || max === undefined)) {
    return '–';
  }
  return `${formatWon(min)}~${formatWon(max)}`;
}

// 한 줄: [이름 | 중간값 · 평균 · 최저~최고]
function statLine(label, values) {
  const line = element('div', 'card-line');
  line.append(
    element('span', 'card-label', label),
    element('span', 'card-values',
      `중간값 ${formatWon(values.median)} · 평균 ${formatWon(values.average)} · 최저~최고 ${range(values.min, values.max)}`),
  );
  return line;
}

function localBox(local, radius) {
  const box = element('div', 'card-box');
  box.append(element('h3', 'card-title', `반경 ${formatRadius(radius)} 안 · 병원별 최저가 기준`));
  if (local) {
    box.append(statLine(`${local.count}곳`, local));
  } else {
    box.append(element('p', 'card-empty', '반경 안 병원이 3곳 미만이라 계산하지 않았어요.'));
  }
  return box;
}

function referenceBox(reference) {
  const box = element('div', 'card-box');
  box.append(element('h3', 'card-title', reference?.stdDate ? `심평원 통계 · ${reference.stdDate} 기준` : '심평원 통계'));
  if (!reference) {
    box.append(element('p', 'card-empty', '이 항목의 심평원 통계가 없어요.'));
    return box;
  }
  if (reference.nationwide) {
    box.append(statLine(reference.nationwide.name, reference.nationwide));
  }
  if (reference.sido) {
    box.append(statLine(reference.sido.name, reference.sido));
  }
  if (reference.byType.length > 0) {
    const types = element('details', 'card-types');
    types.append(element('summary', null, '종별 보기'), ...reference.byType.map(values => statLine(values.name, values)));
    box.append(types);
  }
  return box;
}

export function createPriceCard(container) {
  return {
    hide() {
      container.hidden = true;
      container.replaceChildren();
    },

    show({ local, reference, radius }) {
      container.replaceChildren(localBox(local, radius), referenceBox(reference));
      container.hidden = false;
    },
  };
}
