let currentSessionId = 0;

export function sessionId() {
  return currentSessionId;
}

export function nextSessionId() {
  currentSessionId += 1;
  return currentSessionId;
}
