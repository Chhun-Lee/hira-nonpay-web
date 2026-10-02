// 카카오맵 래퍼. 반경 원, 중심 표시, 종별코드 마커, 선택 표시, 지도 이동 감지를 맡는다.
import { prefersReducedMotion } from './motion.js';

const EARTH_RADIUS_METERS = 6_370_986; // 서버 ST_Distance_Sphere와 같은 기준

function cssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

function toLatLng({ latitude, longitude }) {
  return new window.kakao.maps.LatLng(latitude, longitude);
}

// 서버 BoundingBox와 같은 공식으로 반경 원을 덮는 사각형을 만든다.
function boundsAround({ latitude, longitude }, radiusMeters) {
  const { maps } = window.kakao;
  const angularRadius = radiusMeters / EARTH_RADIUS_METERS;
  const latitudeDelta = (angularRadius * 180) / Math.PI;
  const longitudeDelta = (Math.asin(Math.sin(angularRadius) / Math.cos((latitude * Math.PI) / 180)) * 180) / Math.PI;
  return new maps.LatLngBounds(
    new maps.LatLng(latitude - latitudeDelta, longitude - longitudeDelta),
    new maps.LatLng(latitude + latitudeDelta, longitude + longitudeDelta),
  );
}

function distanceMeters(a, b) {
  const toRadians = degrees => (degrees * Math.PI) / 180;
  const dLat = toRadians(b.latitude - a.latitude);
  const dLng = toRadians(b.longitude - a.longitude);
  const h = Math.sin(dLat / 2) ** 2
    + Math.cos(toRadians(a.latitude)) * Math.cos(toRadians(b.latitude)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
}

export function createMapView(container, { center, onSelect, onMovedAway }) {
  const { maps } = window.kakao;
  const map = new maps.Map(container, { center: toLatLng(center), level: 5 });
  const green = cssVar('--form-green');
  const circle = new maps.Circle({
    center: toLatLng(center),
    radius: 1000,
    strokeWeight: 1.5,
    strokeColor: green,
    strokeOpacity: 1,
    strokeStyle: 'dash',
    fillColor: green,
    fillOpacity: 0.06,
  });
  circle.setMap(map);

  const centerMark = document.createElement('div');
  centerMark.className = 'center-mark';
  const centerOverlay = new maps.CustomOverlay({ position: toLatLng(center), content: centerMark, zIndex: 0 });
  centerOverlay.setMap(map);

  const markers = new Map(); // ykiho → { overlay, button, position }
  let searchCenter = center;
  let searchRadius = 1000;
  let expectedCenter = center; // 코드가 마지막으로 옮긴 위치. 여기로 온 이동은 사용자 이동이 아니다.
  let draggedByUser = false;

  function getCenter() {
    const latLng = map.getCenter();
    return { latitude: latLng.getLat(), longitude: latLng.getLng() };
  }

  function fitSearchArea() {
    expectedCenter = searchCenter;
    map.setBounds(boundsAround(searchCenter, searchRadius), 24, 24, 24, 24);
  }

  maps.event.addListener(map, 'dragend', () => {
    draggedByUser = true;
  });

  // 지도 칸 크기가 바뀌면(창 크기 변경, 화면 회전) 카카오맵이 크기를 다시 재야 중심이 어긋나지 않는다.
  // 사용자가 끌어 옮긴 게 아니면 검색 범위를 다시 화면에 맞춘다.
  new ResizeObserver(() => {
    map.relayout();
    if (!draggedByUser) {
      fitSearchArea();
    }
  }).observe(container);

  // 확대·축소만으로는 검색 범위가 같으므로, 중심이 검색 중심과 코드가 옮긴 위치 모두에서 벗어났을 때만 알린다.
  maps.event.addListener(map, 'idle', () => {
    const current = getCenter();
    const tolerance = Math.max(30, searchRadius * 0.05);
    const movedAway = distanceMeters(current, searchCenter) > tolerance
      && distanceMeters(current, expectedCenter) > tolerance;
    onMovedAway(movedAway);
  });

  function moveTo(position) {
    expectedCenter = position;
    if (prefersReducedMotion()) {
      map.setCenter(toLatLng(position));
    } else {
      map.panTo(toLatLng(position));
    }
  }

  return {
    getCenter,

    setSearchArea(nextCenter, radius) {
      searchCenter = nextCenter;
      searchRadius = radius;
      draggedByUser = false;
      circle.setPosition(toLatLng(nextCenter));
      circle.setRadius(radius);
      centerOverlay.setPosition(toLatLng(nextCenter));
      fitSearchArea();
    },

    showHospitals(hospitals) {
      for (const { overlay } of markers.values()) {
        overlay.setMap(null);
      }
      markers.clear();
      for (const hospital of hospitals) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'marker';
        button.textContent = hospital.clCd ?? '–';
        button.setAttribute('aria-label', hospital.name);
        button.setAttribute('aria-pressed', 'false');
        button.addEventListener('click', () => onSelect(hospital.ykiho));
        const position = { latitude: hospital.latitude, longitude: hospital.longitude };
        const overlay = new maps.CustomOverlay({ position: toLatLng(position), content: button, clickable: true, zIndex: 1 });
        overlay.setMap(map);
        markers.set(hospital.ykiho, { overlay, button, position });
      }
    },

    select(ykiho, { pan }) {
      for (const [key, marker] of markers) {
        const selected = key === ykiho;
        marker.button.classList.toggle('is-selected', selected);
        marker.button.setAttribute('aria-pressed', String(selected));
        marker.overlay.setZIndex(selected ? 2 : 1);
      }
      const marker = markers.get(ykiho);
      if (pan && marker) {
        moveTo(marker.position);
      }
    },
  };
}
