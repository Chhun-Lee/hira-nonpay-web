// DOM 도우미. 서버 데이터는 textContent로만 넣는다(이름·주소에 특수문자가 있어도 글자 그대로 보이게).

export function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) {
    node.className = className;
  }
  if (text !== undefined && text !== null) {
    node.textContent = text;
  }
  return node;
}
