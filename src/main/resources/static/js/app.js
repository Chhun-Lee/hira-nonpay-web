// 화면 상태와 이벤트 연결. 검색 중심·반경과 종별(또는 비급여 항목)을 기준으로 목록과 지도를 다시 그린다.
// 비급여 항목을 고르면 가격 모드가 되고, 항목을 지우면 병원 찾기(3-1)로 돌아간다.
import { comparePrices, fetchFeaturedItems, fetchRegions, searchHospitals } from './api.js';
import { loadKakaoSdk } from './kakao-sdk.js';
import { createListView } from './list-view.js';
import { createMapView } from './map-view.js';
import { createPriceCard } from './price-card.js';

const FALLBACK_CENTER = { latitude: 37.5665, longitude: 126.978 }; // 수집된 지역이 없을 때: 서울시청
const LOAD_ERROR = '병원 목록을 불러오지 못했어요. 잠시 후 다시 검색해 주세요.';
const PRICE_LOAD_ERROR = '가격을 불러오지 못했어요. 잠시 후 다시 검색해 주세요.';
const MAP_LOAD_TIMEOUT_MS = 5000;
const PRICE_MIN_RADIUS = 3000; // 가격 모드에서 반경이 이보다 좁으면
const PRICE_DEFAULT_RADIUS = 5000; // 이 반경으로 넓힌다(병원급 의료기관은 드물다)

const root = document.getElementById('app');
const maxResults = Number(root.dataset.maxResults);
const sgguSelect = document.getElementById('sggu');
const dongSelect = document.getElementById('dong');
const radiusGroup = document.getElementById('radius');
const clCdGroup = document.getElementById('cl-cd');
const clCdField = clCdGroup.closest('.field');
const featuredGroup = document.getElementById('featured');
const itemChosen = document.getElementById('item-chosen');
const itemChosenName = document.getElementById('item-chosen-name');
const itemClearButton = document.getElementById('item-clear');
const listOrder = document.getElementById('list-order');
const sortGroup = document.getElementById('price-sort');
const clinicNotice = document.getElementById('clinic-notice');
const searchHereButton = document.getElementById('search-here');
const mapError = document.getElementById('map-error');

const state = {
  center: FALLBACK_CENTER,
  radius: 1000,
  clCd: '',
  item: null, // { code, label } — 있으면 가격 모드
  sort: 'price',
  radiusNotice: null, // 반경을 넓혔다는 알림. 다음 검색에서 한 번만 보인다
  regions: [],
  hospitals: [],
};
let map = null;
let latestSearchId = 0;

const list = createListView({
  summary: document.getElementById('list-summary'),
  notice: document.getElementById('list-notice'),
  rows: document.getElementById('rows'),
  onSelect: ykiho => select(ykiho, 'list'),
});
const priceCard = createPriceCard(document.getElementById('price-card'));

function select(ykiho, from) {
  list.select(ykiho, { scroll: from === 'map' });
  map?.select(ykiho, { pan: from === 'list' });
}

async function search() {
  const searchId = ++latestSearchId;
  const notice = state.radiusNotice;
  state.radiusNotice = null;
  searchHereButton.hidden = true;
  list.showLoading();
  priceCard.hide();
  map?.setSearchArea(state.center, state.radius);
  try {
    if (state.item) {
      const result = await comparePrices({
        itemCd: state.item.code, ...state.center, radius: state.radius, sort: state.sort,
      });
      if (searchId !== latestSearchId) {
        return; // 더 최근 검색이 있으면 늦게 온 응답은 버린다
      }
      state.hospitals = result.hospitals;
      list.renderPrices({
        hospitals: result.hospitals, total: result.total, radius: state.radius, sort: state.sort, notice,
      });
      priceCard.show({ local: result.local, reference: result.reference, radius: state.radius });
    } else {
      const result = await searchHospitals({ ...state.center, radius: state.radius, clCd: state.clCd });
      if (searchId !== latestSearchId) {
        return;
      }
      state.hospitals = result.hospitals;
      list.render({ hospitals: state.hospitals, radius: state.radius, maxResults });
    }
    map?.showHospitals(state.hospitals);
  } catch {
    if (searchId !== latestSearchId) {
      return;
    }
    state.hospitals = [];
    list.showError(state.item ? PRICE_LOAD_ERROR : LOAD_ERROR);
    map?.showHospitals([]);
  }
}

function pressOnly(group, pressed) {
  for (const button of group.querySelectorAll('button')) {
    button.setAttribute('aria-pressed', String(button === pressed));
  }
}

function pressFeatured(code) {
  for (const button of featuredGroup.querySelectorAll('button')) {
    button.setAttribute('aria-pressed', String(button.dataset.code === code));
  }
}

function setPriceMode(on) {
  clCdField.hidden = on; // 가격 데이터는 병원급뿐이라 종별 버튼은 뜻이 없다
  sortGroup.hidden = !on;
  listOrder.hidden = on;
  clinicNotice.hidden = !on;
  itemChosen.hidden = !on;
}

// 빠른 선택이나 검색(Task 7)으로 항목을 고른다. item: { code, label }
function chooseItem(item) {
  state.item = item;
  itemChosenName.textContent = item.label;
  pressFeatured(item.code);
  if (state.radius < PRICE_MIN_RADIUS) {
    state.radius = PRICE_DEFAULT_RADIUS;
    pressOnly(radiusGroup, radiusGroup.querySelector(`button[data-radius="${PRICE_DEFAULT_RADIUS}"]`));
    state.radiusNotice = '병원급 의료기관은 드물어서 반경을 5km로 넓혔어요.';
  }
  setPriceMode(true);
  search();
}

function clearItem() {
  state.item = null;
  pressFeatured(null);
  setPriceMode(false);
  search();
}

function option(value, text) {
  const node = document.createElement('option');
  node.value = value;
  node.textContent = text;
  return node;
}

function centerOf(place) {
  return { latitude: place.latitude, longitude: place.longitude };
}

function currentRegion() {
  return state.regions.find(region => region.sgguCd === sgguSelect.value);
}

function fillDongs(region) {
  dongSelect.replaceChildren(option('', '동 전체'), ...(region?.dongs ?? []).map(dong => option(dong.name, dong.name)));
}

sgguSelect.addEventListener('change', () => {
  const region = currentRegion();
  fillDongs(region);
  if (region) {
    state.center = centerOf(region);
    search();
  }
});

dongSelect.addEventListener('change', () => {
  const region = currentRegion();
  if (!region) {
    return;
  }
  const dong = region.dongs.find(candidate => candidate.name === dongSelect.value);
  state.center = centerOf(dong ?? region);
  search();
});

radiusGroup.addEventListener('click', event => {
  const button = event.target.closest('button[data-radius]');
  if (!button) {
    return;
  }
  pressOnly(radiusGroup, button);
  state.radius = Number(button.dataset.radius);
  search();
});

clCdGroup.addEventListener('click', event => {
  const button = event.target.closest('button[data-cl-cd]');
  if (!button) {
    return;
  }
  pressOnly(clCdGroup, button);
  state.clCd = button.dataset.clCd;
  search();
});

featuredGroup.addEventListener('click', event => {
  const button = event.target.closest('button[data-code]');
  if (!button || state.item?.code === button.dataset.code) {
    return;
  }
  chooseItem({ code: button.dataset.code, label: button.textContent });
});

itemClearButton.addEventListener('click', clearItem);

sortGroup.addEventListener('click', event => {
  const button = event.target.closest('button[data-sort]');
  if (!button || button.dataset.sort === state.sort) {
    return;
  }
  pressOnly(sortGroup, button);
  state.sort = button.dataset.sort;
  search();
});

searchHereButton.addEventListener('click', () => {
  if (map) {
    state.center = map.getCenter();
    search();
  }
});

async function loadRegions() {
  try {
    state.regions = await fetchRegions();
  } catch {
    state.regions = [];
  }
  if (state.regions.length === 0) {
    sgguSelect.replaceChildren(option('', '수집된 지역 없음'));
    sgguSelect.disabled = true;
    dongSelect.disabled = true;
    return;
  }
  sgguSelect.replaceChildren(
    ...state.regions.map(region => option(region.sgguCd, [region.sidoCdNm, region.sgguCdNm].filter(Boolean).join(' '))),
  );
  const initial = state.regions.find(region => region.sgguCd === root.dataset.defaultSgguCd) ?? state.regions[0];
  sgguSelect.value = initial.sgguCd;
  fillDongs(initial);
  state.center = centerOf(initial);
}

// 빠른 선택 버튼. 불러오지 못하면 버튼 줄만 숨긴다(병원 찾기는 그대로 된다).
async function loadFeatured() {
  let items = [];
  try {
    items = (await fetchFeaturedItems()).items;
  } catch {
    items = [];
  }
  featuredGroup.replaceChildren(...items.map(item => {
    const button = document.createElement('button');
    button.type = 'button';
    button.dataset.code = item.code;
    button.textContent = item.label ?? item.detail;
    button.title = item.name;
    button.setAttribute('aria-pressed', String(state.item?.code === item.code));
    return button;
  }));
  featuredGroup.hidden = items.length === 0;
}

function startMap() {
  map = createMapView(document.getElementById('map'), {
    center: state.center,
    onSelect: ykiho => select(ykiho, 'map'),
    onMovedAway: movedAway => {
      searchHereButton.hidden = !movedAway;
    },
  });
}

// 지도는 목록 검색과 따로 준비한다. 준비되면 그때까지의 검색 범위와 결과를 지도에 그린다.
async function loadMap() {
  try {
    await loadKakaoSdk(root.dataset.kakaoSdk, MAP_LOAD_TIMEOUT_MS);
    startMap();
  } catch {
    map = null;
    mapError.hidden = false;
    return;
  }
  map.setSearchArea(state.center, state.radius);
  map.showHospitals(state.hospitals);
}

async function start() {
  loadMap(); // 기다리지 않는다: 지도 SDK가 늦거나 실패해도 목록 검색은 바로 시작한다
  loadFeatured(); // 기다리지 않는다: 빠른 선택이 늦어도 병원 찾기는 바로 된다
  await loadRegions();
  search();
}

start();
