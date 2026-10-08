// LeagueTable.java - Football League Table (Backend + Frontend in ONE file)
// Run: java LeagueTable.java  -> open http://localhost:4000

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LeagueTable {

    // ---------- BACKEND DATA ----------
    static class Team {
        String name;
        int played, won, drawn, lost, gf, ga, points;

        Team(String name) { this.name = name; }

        String toJson() {
            return "{\"team\":\"" + name + "\",\"played\":" + played + ",\"won\":" + won
                    + ",\"drawn\":" + drawn + ",\"lost\":" + lost + ",\"gf\":" + gf
                    + ",\"ga\":" + ga + ",\"points\":" + points + "}";
        }
    }

    static final Map<String, Team> teams = new LinkedHashMap<>();

    static {
        for (String n : List.of("Barcelona", "Real Madrid", "Man City", "Liverpool", "Arsenal")) {
            teams.put(n, new Team(n));
        }
    }

    static synchronized String getTableJson() {
        List<Team> rows = new ArrayList<>(teams.values());
        // sort: points, then goal difference, then goals scored
        rows.sort(Comparator
                .comparingInt((Team t) -> t.points)
                .thenComparingInt(t -> t.gf - t.ga)
                .thenComparingInt(t -> t.gf)
                .reversed());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(rows.get(i).toJson());
        }
        return sb.append("]").toString();
    }

    static synchronized void addMatch(String home, String away, int hg, int ag) {
        Team h = teams.get(home), a = teams.get(away);
        h.played++; a.played++;
        h.gf += hg; h.ga += ag;
        a.gf += ag; a.ga += hg;
        if (hg > ag) { h.won++; h.points += 3; a.lost++; }
        else if (hg < ag) { a.won++; a.points += 3; h.lost++; }
        else { h.drawn++; a.drawn++; h.points++; a.points++; }
    }

    // ---------- FRONTEND ----------
    static final String HTML = """
            <!DOCTYPE html>
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
            </html>
            """;

    // ---------- HELPERS ----------
    static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static String field(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\"([^\"]*)\"|(-?\\d+))").matcher(json);
        if (!m.find()) return "";
        return m.group(2) != null ? m.group(2) : m.group(3);
    }

    // ---------- BACKEND SERVER ----------
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(4000), 0);

        server.createContext("/", ex -> {
            if (ex.getRequestURI().getPath().equals("/")) send(ex, 200, "text/html", HTML);
            else send(ex, 404, "text/plain", "Not found");
        });

        server.createContext("/api/table", ex ->
                send(ex, 200, "application/json", getTableJson()));

        server.createContext("/api/match", ex -> {
            if (!ex.getRequestMethod().equals("POST")) {
                send(ex, 405, "text/plain", "Method not allowed");
                return;
            }
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String home = field(body, "home"), away = field(body, "away");
            if (home.equals(away)) {
                send(ex, 400, "text/plain", "Home and away team must be different");
                return;
            }
            if (!teams.containsKey(home) || !teams.containsKey(away)) {
                send(ex, 400, "text/plain", "Unknown team");
                return;
            }
            addMatch(home, away, Integer.parseInt(field(body, "hg")), Integer.parseInt(field(body, "ag")));
            send(ex, 201, "application/json", "{\"ok\":true}");
        });

        server.start();
        System.out.println("Running at http://localhost:4000");
    }
}
