// 화면 상태와 이벤트 연결. 검색 중심·반경·종별을 기준으로 목록과 지도를 다시 그린다.
import { fetchRegions, searchHospitals } from './api.js';
import { loadKakaoSdk } from './kakao-sdk.js';
import { createListView } from './list-view.js';
import { createMapView } from './map-view.js';

const FALLBACK_CENTER = { latitude: 37.5665, longitude: 126.978 }; // 수집된 지역이 없을 때: 서울시청
const LOAD_ERROR = '병원 목록을 불러오지 못했어요. 잠시 후 다시 검색해 주세요.';
const MAP_LOAD_TIMEOUT_MS = 5000;

const root = document.getElementById('app');
const maxResults = Number(root.dataset.maxResults);
const sgguSelect = document.getElementById('sggu');
const dongSelect = document.getElementById('dong');
const radiusGroup = document.getElementById('radius');
const clCdGroup = document.getElementById('cl-cd');
const searchHereButton = document.getElementById('search-here');
const mapError = document.getElementById('map-error');

const state = { center: FALLBACK_CENTER, radius: 1000, clCd: '', regions: [], hospitals: [] };
let map = null;
let latestSearchId = 0;

const list = createListView({
  summary: document.getElementById('list-summary'),
  notice: document.getElementById('list-notice'),
  rows: document.getElementById('rows'),
  onSelect: ykiho => select(ykiho, 'list'),
});

function select(ykiho, from) {
  list.select(ykiho, { scroll: from === 'map' });
  map?.select(ykiho, { pan: from === 'list' });
}

async function search() {
  const searchId = ++latestSearchId;
  searchHereButton.hidden = true;
  list.showLoading();
  map?.setSearchArea(state.center, state.radius);
  try {
    const result = await searchHospitals({ ...state.center, radius: state.radius, clCd: state.clCd });
    if (searchId !== latestSearchId) {
      return; // 더 최근 검색이 있으면 늦게 온 응답은 버린다
    }
    state.hospitals = result.hospitals;
    list.render({ hospitals: state.hospitals, radius: state.radius, maxResults });
    map?.showHospitals(state.hospitals);
  } catch {
    if (searchId !== latestSearchId) {
      return;
    }
    state.hospitals = [];
    list.showError(LOAD_ERROR);
    map?.showHospitals([]);
  }
}

function pressOnly(group, pressed) {
  for (const button of group.querySelectorAll('button')) {
    button.setAttribute('aria-pressed', String(button === pressed));
  }
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
  await loadRegions();
  search();
}

start();
