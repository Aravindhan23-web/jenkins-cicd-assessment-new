# league_table.py - Football League Table (Backend + Frontend in ONE file)
# Run: python3 league_table.py  -> open http://localhost:4000

import json
from http.server import BaseHTTPRequestHandler, HTTPServer

# ---------- BACKEND DATA ----------
teams = {
    name: {"team": name, "played": 0, "won": 0, "drawn": 0, "lost": 0, "gf": 0, "ga": 0, "points": 0}
    for name in ["Barcelona", "Real Madrid", "Man City", "Liverpool", "Arsenal"]
}
results = []  # match history


def get_table():
    rows = list(teams.values())
    # sort: points, then goal difference, then goals scored
    rows.sort(key=lambda t: (t["points"], t["gf"] - t["ga"], t["gf"]), reverse=True)
    return rows


def add_match(home, away, hg, ag):
    h, a = teams[home], teams[away]
    h["played"] += 1
    a["played"] += 1
    h["gf"] += hg; h["ga"] += ag
    a["gf"] += ag; a["ga"] += hg
    if hg > ag:
        h["won"] += 1; h["points"] += 3; a["lost"] += 1
    elif hg < ag:
        a["won"] += 1; a["points"] += 3; h["lost"] += 1
    else:
        h["drawn"] += 1; a["drawn"] += 1
        h["points"] += 1; a["points"] += 1
    results.append(f"{home} {hg} - {ag} {away}")


# ---------- FRONTEND ----------
HTML = """<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>Football League Table</title>
  <style>
    body { font-family: Arial; max-width: 700px; margin: 40px auto; }
    h1 { color: #0b5ed7; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border-bottom: 1px solid #ddd; padding: 8px; text-align: center; }
    th { background: #0b5ed7; color: white; }
    td:nth-child(2) { text-align: left; }
    select, input, button { padding: 6px; margin: 2px; }
    .box { background: #f4f4f4; padding: 12px; border-radius: 6px; margin-top: 20px; }
  </style>
</head>
<body>
  <h1>🏆 League Table</h1>
  <table>
    <thead>
      <tr><th>#</th><th>Team</th><th>P</th><th>W</th><th>D</th><th>L</th><th>GD</th><th>Pts</th></tr>
    </thead>
    <tbody id="rows"></tbody>
  </table>

  <div class="box">
    <h3>Add Match Result</h3>
    <select id="home"></select>
    <input id="hg" type="number" min="0" value="0" style="width:50px"> -
    <input id="ag" type="number" min="0" value="0" style="width:50px">
    <select id="away"></select>
    <button onclick="addMatch()">Add</button>
    <p id="msg"></p>
  </div>

  <script>
    async function load() {
      const res = await fetch("/api/table");
      const table = await res.json();
      document.getElementById("rows").innerHTML = table.map((t, i) =>
        "<tr><td>" + (i + 1) + "</td><td>" + t.team + "</td><td>" + t.played + "</td><td>" + t.won +
        "</td><td>" + t.drawn + "</td><td>" + t.lost + "</td><td>" + (t.gf - t.ga) +
        "</td><td><b>" + t.points + "</b></td></tr>").join("");
      const opts = table.map(t => "<option>" + t.team + "</option>").join("");
      const h = document.getElementById("home"), a = document.getElementById("away");
      if (!h.options.length) { h.innerHTML = opts; a.innerHTML = opts; a.selectedIndex = 1; }
    }
    async function addMatch() {
      const body = {
        home: document.getElementById("home").value,
        away: document.getElementById("away").value,
        hg: Number(document.getElementById("hg").value),
        ag: Number(document.getElementById("ag").value),
      };
      const res = await fetch("/api/match", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      document.getElementById("msg").innerText = res.ok ? "Added!" : await res.text();
      load();
    }
    load();
  </script>
</body>
</html>"""


# ---------- BACKEND SERVER ----------
class Handler(BaseHTTPRequestHandler):
    def _send(self, code, content_type, body):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type + "; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/":
            self._send(200, "text/html", HTML)
        elif self.path == "/api/table":
            self._send(200, "application/json", json.dumps(get_table()))
        elif self.path == "/api/results":
            self._send(200, "application/json", json.dumps(results))
        else:
            self._send(404, "text/plain", "Not found")

    def do_POST(self):
        if self.path == "/api/match":
            length = int(self.headers.get("Content-Length", 0))
            m = json.loads(self.rfile.read(length))
            if m["home"] == m["away"]:
                return self._send(400, "text/plain", "Home and away team must be different")
            add_match(m["home"], m["away"], int(m["hg"]), int(m["ag"]))
            self._send(201, "application/json", json.dumps({"ok": True}))
        else:
            self._send(404, "text/plain", "Not found")


if __name__ == "__main__":
    print("Running at http://localhost:4000")
    HTTPServer(("", 4000), Handler).serve_forever()
