const SHEET_NAME = 'P2C保存データ';

function doGet() {
  return jsonResponse(readState());
}

function doPost(event) {
  const payload = JSON.parse(event.postData.contents);
  const expectedToken = PropertiesService.getScriptProperties().getProperty('P2C_TOKEN');
  if (!expectedToken || payload.token !== expectedToken) {
    return jsonResponse({ ok: false, error: 'Unauthorized' });
  }

  const sheet = getSheet();
  sheet.clearContents();
  sheet.getRange(1, 1, 1, 3).setValues([['出席番号', '提出段階', '欠席']]);
  const steps = payload.steps || {};
  const absentNumbers = new Set(payload.absentNumbers || []);
  const rows = [];
  for (let number = 1; number <= 40; number++) {
    rows.push([number, Number(steps[number] || 0), absentNumbers.has(number)]);
  }
  sheet.getRange(2, 1, rows.length, 3).setValues(rows);
  return jsonResponse({ ok: true });
}

function readState() {
  const values = getSheet().getDataRange().getValues();
  const steps = {};
  const absentNumbers = [];
  for (let index = 1; index < values.length; index++) {
    const [number, step, absent] = values[index];
    if (!number) continue;
    steps[number] = Number(step || 0);
    if (absent === true) absentNumbers.push(Number(number));
  }
  return { steps, absentNumbers };
}

function getSheet() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  return spreadsheet.getSheetByName(SHEET_NAME) || spreadsheet.insertSheet(SHEET_NAME);
}

function jsonResponse(value) {
  return ContentService.createTextOutput(JSON.stringify(value))
    .setMimeType(ContentService.MimeType.JSON);
}
