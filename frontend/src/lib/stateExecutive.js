const order = ['PRESIDENT', 'WORKING_PRESIDENT', 'VICE_PRESIDENT', 'SECRETARY', 'TREASURER'];
export function stateExecutiveSeats(records = []) {
  return [...records].sort((a, b) => String(a.committeeName).localeCompare(String(b.committeeName)) ||
    (order.indexOf(a.positionType) - order.indexOf(b.positionType)) || Number(a.positionNumber) - Number(b.positionNumber));
}
