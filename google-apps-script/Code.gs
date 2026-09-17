/**
 * Nill Tracker Google Apps Script backend.
 *
 * Attach this script to the Google Sheet that should store your sessions.
 * Change TOKEN before deploying.
 */
const CONFIG = {
  SHEET_NAME: 'Sessions',
  TOKEN: 'CHANGE_THIS_TO_A_LONG_RANDOM_SECRET',
  TIME_ZONE: Session.getScriptTimeZone() || 'Asia/Dhaka'
};

function doGet(e) {
  return handleRequest_(e && e.parameter ? e.parameter : {});
}

function doPost(e) {
  let p = {};
  try {
    p = JSON.parse(e.postData.contents || '{}');
  } catch (_) {
    p = e && e.parameter ? e.parameter : {};
  }
  return handleRequest_(p);
}

function handleRequest_(p) {
  try {
    if (p.token !== CONFIG.TOKEN) return json_({ok:false, error:'Unauthorized'});

    const action = String(p.action || 'summary');
    ensureSheet_();

    if (action === 'log') {
      const start = new Date(p.start);
      const end = new Date(p.end);
      const duration = Number(p.durationSeconds || Math.round((end - start) / 1000));
      if (isNaN(start.getTime()) || isNaN(end.getTime()) || duration <= 0) {
        return json_({ok:false, error:'Invalid session'});
      }

      const sheet = SpreadsheetApp.getActive().getSheetByName(CONFIG.SHEET_NAME);
      sheet.appendRow([
        new Date(),
        start.toISOString(),
        end.toISOString(),
        duration,
        String(p.device || 'Unknown')
      ]);
      return json_({ok:true});
    }

    if (action === 'summary') return json_(buildSummary_());

    return json_({ok:false, error:'Unknown action'});
  } catch (err) {
    return json_({ok:false, error:String(err)});
  }
}

function ensureSheet_() {
  const ss = SpreadsheetApp.getActive();
  let sheet = ss.getSheetByName(CONFIG.SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(CONFIG.SHEET_NAME);
    sheet.appendRow(['Logged At', 'Start', 'End', 'Duration Seconds', 'Device']);
    sheet.setFrozenRows(1);
  }
}

function buildSummary_() {
  const sheet = SpreadsheetApp.getActive().getSheetByName(CONFIG.SHEET_NAME);
  const values = sheet.getDataRange().getValues();
  const sessions = [];

  for (let i = 1; i < values.length; i++) {
    const start = new Date(values[i][1]);
    const end = new Date(values[i][2]);
    const duration = Number(values[i][3]) || 0;
    if (isNaN(start.getTime()) || isNaN(end.getTime()) || duration <= 0) continue;

    sessions.push({
      start: start.toISOString(),
      end: end.toISOString(),
      durationSeconds: duration,
      device: String(values[i][4] || 'Unknown')
    });
  }

  sessions.sort((a,b) => new Date(b.start) - new Date(a.start));

  const now = new Date();
  const todayKey = dayKey_(now);
  const weekStart = startOfWeek_(now);
  let today = 0, week = 0;
  const activeDays = {};

  sessions.forEach(s => {
    const d = new Date(s.start);
    const sec = s.durationSeconds;
    if (dayKey_(d) === todayKey) today += sec;
    if (d >= weekStart) week += sec;
    activeDays[dayKey_(d)] = true;
  });

  let streak = 0;
  let cursor = new Date(now);
  if (!activeDays[dayKey_(cursor)]) {
    cursor.setDate(cursor.getDate() - 1);
  }
  while (activeDays[dayKey_(cursor)]) {
    streak++;
    cursor.setDate(cursor.getDate() - 1);
  }

  return {
    ok: true,
    todaySeconds: today,
    weekSeconds: week,
    streak: streak,
    sessions: sessions.slice(0, 100)
  };
}

function dayKey_(d) {
  return Utilities.formatDate(d, CONFIG.TIME_ZONE, 'yyyy-MM-dd');
}

function startOfWeek_(d) {
  const x = new Date(d);
  x.setHours(0,0,0,0);
  const day = x.getDay(); // Sunday=0
  x.setDate(x.getDate() - day);
  return x;
}

function json_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
