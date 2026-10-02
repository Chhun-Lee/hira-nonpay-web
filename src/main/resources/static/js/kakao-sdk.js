// 카카오맵 SDK를 페이지 파싱과 분리해 불러온다. SDK 서버가 응답하지 않아도 목록 검색은 먼저 시작할 수 있다.
// 실패하거나 timeoutMs 안에 준비되지 않으면 reject한다.
export function loadKakaoSdk(url, timeoutMs) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('카카오 SDK 응답 없음')), timeoutMs);
    const fail = message => {
      clearTimeout(timer);
      reject(new Error(message));
    };

    const script = document.createElement('script');
    script.src = url;
    script.async = true;
    script.addEventListener('error', () => fail('카카오 SDK 로드 실패'));
    script.addEventListener('load', () => {
      if (!window.kakao?.maps) {
        fail('카카오 SDK 초기화 실패');
        return;
      }
      window.kakao.maps.load(() => {
        clearTimeout(timer);
        resolve(window.kakao.maps);
      });
    });
    document.head.append(script);
  });
}
