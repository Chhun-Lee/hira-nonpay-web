// 비급여 항목 검색 칸(콤보박스). 입력이 멈추면(300ms) 항목 검색 API를 부르고 후보를 펼친다.
// 늦게 온 응답은 버린다. 후보 데이터는 textContent로만 넣는다.
import { searchItems } from './api.js';
import { element } from './dom.js';

const DEBOUNCE_MS = 300;
const MAX_QUERY_LENGTH = 50; // 서버 검증과 같다

export function createItemSearch({ input, options, onChoose }) {
  let timer = null;
  let latest = 0; // 마지막으로 보낸 요청 번호
  let candidates = [];
  let active = -1;

  function open() {
    options.hidden = false;
    input.setAttribute('aria-expanded', 'true');
  }

  function close() {
    options.hidden = true;
    options.replaceChildren();
    input.setAttribute('aria-expanded', 'false');
    input.removeAttribute('aria-activedescendant');
    candidates = [];
    active = -1;
  }

  function showMessage(text) {
    candidates = [];
    active = -1;
    input.removeAttribute('aria-activedescendant');
    options.replaceChildren(element('li', 'item-option-message', text));
    open();
  }

  function highlight(index) {
    active = index;
    const nodes = options.querySelectorAll('[role="option"]');
    nodes.forEach((node, i) => node.setAttribute('aria-selected', String(i === index)));
    const node = nodes[index];
    if (node) {
      input.setAttribute('aria-activedescendant', node.id);
      node.scrollIntoView({ block: 'nearest' });
    }
  }

  function choose(index) {
    const item = candidates[index];
    if (!item) {
      return;
    }
    close();
    input.value = '';
    onChoose({ code: item.code, label: item.detail });
  }

  function show(items) {
    if (items.length === 0) {
      showMessage('찾는 항목이 없어요. 다른 이름으로 찾아보세요.');
      return;
    }
    candidates = items;
    active = -1;
    input.removeAttribute('aria-activedescendant');
    options.replaceChildren(...items.map((item, index) => {
      const option = element('li', 'item-option');
      option.id = `item-option-${index}`;
      option.setAttribute('role', 'option');
      option.setAttribute('aria-selected', 'false');
      option.append(
        element('strong', 'item-option-detail', item.detail),
        element('span', 'item-option-category', item.category),
        element('span', 'item-option-count', `${item.hospitalCount.toLocaleString('ko-KR')}곳`),
      );
      option.addEventListener('mousedown', event => event.preventDefault()); // 입력 칸 포커스를 잃지 않게
      option.addEventListener('click', () => choose(index));
      return option;
    }));
    open();
  }

  async function run(query) {
    const requestId = ++latest;
    try {
      const result = await searchItems(query);
      if (requestId !== latest) {
        return;
      }
      show(result.items);
    } catch {
      if (requestId !== latest) {
        return;
      }
      showMessage('항목을 찾지 못했어요. 잠시 후 다시 입력해 주세요.');
    }
  }

  input.addEventListener('input', () => {
    clearTimeout(timer);
    const query = input.value.trim().slice(0, MAX_QUERY_LENGTH);
    if (query === '') {
      latest++; // 이미 보낸 요청의 응답은 버린다
      close();
      return;
    }
    timer = setTimeout(() => run(query), DEBOUNCE_MS);
  });

  input.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      close();
      return;
    }
    if (candidates.length === 0) {
      return;
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      highlight((active + 1) % candidates.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      highlight(active <= 0 ? candidates.length - 1 : active - 1);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      choose(active >= 0 ? active : 0);
    }
  });

  input.addEventListener('blur', close);
}
