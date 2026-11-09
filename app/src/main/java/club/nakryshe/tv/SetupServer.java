package club.nakryshe.tv;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Крошечный веб-сервер для настройки: пока на приставке открыт экран настройки, с телефона или компьютера в той же сети
 * можно открыть http://адрес-приставки:порт и вставить ссылку комнаты — не нужно набирать длинный ключ пультом.
 * Работает только пока экран настройки открыт. Ничего от Android не использует, поэтому проверяется обычной Java.
 */
final class SetupServer {
    interface Listener {
        /** Принять ссылку. Вернуть null — принято, иначе текст ошибки для страницы. */
        String onSubmit(String url);

        /** Одна строка о том, что настроено сейчас (показывается на странице). */
        String status();

        /** Приложения для плиток: {пакет, название, "1" если плитка сейчас включена}. Сначала включённые, в их порядке. */
        List<String[]> apps();

        /** Сохранить плитки (пакеты по порядку; пустой список — без плиток). Вернуть null — принято, иначе текст ошибки. */
        String onTiles(List<String> packages);

        /** Вернуть стандартный набор плиток. */
        void onTilesReset();
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final int port;
    private final Listener listener;
    private volatile ServerSocket server;

    SetupServer(int port, Listener listener) {
        this.port = port;
        this.listener = listener;
    }

    void start() throws IOException {
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(port));
        server = s;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "nktv-setup");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        ServerSocket s = server;
        server = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void loop() {
        while (true) {
            ServerSocket s = server;
            if (s == null || s.isClosed()) return;
            try {
                Socket c = s.accept();
                try {
                    c.setSoTimeout(5000);
                    handle(c);
                } finally {
                    try {
                        c.close();
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException e) {
                if (server == null) return; // закрыли намеренно
            } catch (RuntimeException ignored) {
                // кривой запрос не должен ронять настройку
            }
        }
    }

    private void handle(Socket c) throws IOException {
        InputStream in = new BufferedInputStream(c.getInputStream());
        OutputStream out = c.getOutputStream();
        String req = readLine(in);
        if (req == null || req.isEmpty()) return;
        int len = 0;
        String h;
        while ((h = readLine(in)) != null && !h.isEmpty()) {
            if (h.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                try {
                    len = Integer.parseInt(h.substring(15).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String[] parts = req.split(" ");
        String method = parts[0];
        String path = parts.length > 1 ? parts[1] : "/";
        if (len < 0 || len > 8192) {
            respond(out, 413, "text/plain; charset=utf-8", "Слишком длинный запрос");
            return;
        }
        byte[] body = new byte[len];
        int got = 0;
        while (got < len) {
            int r = in.read(body, got, len - got);
            if (r < 0) break;
            got += r;
        }
        if ("POST".equals(method) && path.startsWith("/save")) {
            String url = formValue(new String(body, 0, got, UTF8), "url");
            String err = listener.onSubmit(url);
            respond(out, 200, "text/html; charset=utf-8", err == null ? donePage() : formPage(err, url));
        } else if ("POST".equals(method) && path.startsWith("/tiles-reset")) {
            listener.onTilesReset();
            respond(out, 200, "text/html; charset=utf-8", tilesDonePage("Плитки вернулись к стандартным: Okko, ivi, YouTube (те, что установлены на приставке)."));
        } else if ("POST".equals(method) && path.startsWith("/tiles")) {
            String csv = formValue(new String(body, 0, got, UTF8), "tiles");
            List<String> pkgs = new ArrayList<String>();
            for (String p : csv.split(",")) {
                String t = p.trim();
                if (!t.isEmpty() && !pkgs.contains(t)) pkgs.add(t);
            }
            String err = listener.onTiles(pkgs);
            respond(out, 200, "text/html; charset=utf-8", err == null
                    ? tilesDonePage(pkgs.isEmpty() ? "Плитки выключены: панель приложений не будет появляться." : "Плитки сохранены. На телевизоре они появятся при следующем нажатии кнопки пульта.")
                    : formPage(err, ""));
        } else if ("GET".equals(method) && (path.equals("/") || path.startsWith("/?"))) {
            respond(out, 200, "text/html; charset=utf-8", formPage(null, ""));
        } else {
            respond(out, 404, "text/plain; charset=utf-8", "Не найдено");
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) >= 0) {
            if (ch == '\n') break;
            if (ch != '\r') b.write(ch);
            if (b.size() > 4096) throw new IOException("строка слишком длинная");
        }
        if (ch < 0 && b.size() == 0) return null;
        return new String(b.toByteArray(), UTF8);
    }

    static String formValue(String body, String name) {
        for (String pair : body.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) continue;
            try {
                if (URLDecoder.decode(pair.substring(0, i), "UTF-8").equals(name)) {
                    return URLDecoder.decode(pair.substring(i + 1), "UTF-8");
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private static void respond(OutputStream out, int code, String type, String text) throws IOException {
        byte[] data = text.getBytes(UTF8);
        String status = code == 200 ? "OK" : code == 404 ? "Not Found" : code == 413 ? "Payload Too Large" : "Error";
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(UTF8));
        out.write(data);
        out.flush();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String STYLE = "<style>"
            + "body{margin:0;background:#1E1004;color:#F0DFC0;font-family:-apple-system,Segoe UI,Roboto,sans-serif;padding:28px 18px}"
            + ".w{max-width:560px;margin:0 auto}"
            + "h1{font-size:15px;letter-spacing:3px;text-transform:uppercase;color:#B87333;font-weight:500;margin:0 0 18px}"
            + "p{line-height:1.5;color:#C9A880;font-size:15px}"
            + "textarea{width:100%;box-sizing:border-box;min-height:120px;border-radius:14px;border:1px solid rgba(184,115,51,.45);"
            + "background:#20120A;color:#F0DFC0;padding:14px;font-size:15px;font-family:inherit}"
            + "button{margin-top:14px;width:100%;padding:15px;border:0;border-radius:100px;background:#B87333;color:#1E1004;font-size:16px;font-weight:600}"
            + ".err{background:rgba(239,68,68,.12);border:1px solid rgba(239,68,68,.4);color:#fca5a5;border-radius:12px;padding:12px 14px;margin-bottom:14px;font-size:14px;line-height:1.45}"
            + ".st{font-size:13px;color:#8A6A52;margin-bottom:16px}"
            + "h2{font-size:15px;letter-spacing:3px;text-transform:uppercase;color:#B87333;font-weight:500;margin:34px 0 10px}"
            + "ul.ap{list-style:none;margin:12px 0 0;padding:0}"
            + "ul.ap li{display:flex;align-items:center;justify-content:space-between;background:#20120A;border:1px solid rgba(184,115,51,.25);border-radius:12px;padding:10px 12px;margin-bottom:8px}"
            + "ul.ap label{display:flex;align-items:center;gap:10px;flex:1;font-size:16px;color:#F0DFC0}"
            + "ul.ap input{width:20px;height:20px;accent-color:#B87333}"
            + "ul.ap .mv button{width:auto;margin:0 0 0 6px;padding:6px 14px;border-radius:10px;background:#2A160F;color:#C9A880;font-size:15px;font-weight:400;border:1px solid rgba(184,115,51,.4)}"
            + "button.sec{background:transparent;color:#C9A880;border:1px solid rgba(184,115,51,.45);margin-top:10px}"
            + "</style>";

    private String formPage(String error, String value) {
        return "<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>ТВ · настройка</title>" + STYLE
                + "</head><body><div class=\"w\"><h1>На Крыше · экран для ТВ</h1>"
                + "<div class=\"st\">" + esc(listener.status()) + "</div>"
                + (error == null ? "" : "<div class=\"err\">" + esc(error) + "</div>")
                + "<p>Вставьте сюда ссылку комнаты из Jarvis («Сервис → ТВ-экраны» → выбрать комнату → «Скопировать») и нажмите «Сохранить».</p>"
                + "<form method=\"post\" action=\"/save\"><textarea name=\"url\" placeholder=\"https://…/?room=1&amp;key=…\" autocomplete=\"off\" autocapitalize=\"off\" spellcheck=\"false\">"
                + esc(value) + "</textarea><button type=\"submit\">Сохранить</button></form>"
                + tilesSection() + "</div></body></html>";
    }

    /** Список приложений приставки: галочка включает плитку, стрелки меняют порядок (слева направо на экране). */
    private String tilesSection() {
        StringBuilder b = new StringBuilder();
        b.append("<h2>Плитки приложений</h2>");
        b.append("<p>Кнопки внизу экрана: появляются по любой кнопке пульта. Отметьте приложения (не больше 8) и расставьте стрелками по порядку слева направо.</p>");
        List<String[]> apps = listener.apps();
        if (apps == null || apps.isEmpty()) {
            b.append("<p>Не нашёл на приставке приложений, которые можно открыть с пульта.</p>");
            return b.toString();
        }
        b.append("<ul class=\"ap\" id=\"ap\">");
        for (String[] a : apps) {
            if (a == null || a.length < 3) continue;
            b.append("<li data-p=\"").append(esc(a[0])).append("\"><label><input type=\"checkbox\"")
                    .append("1".equals(a[2]) ? " checked" : "").append("> ").append(esc(a[1])).append("</label>")
                    .append("<span class=\"mv\"><button type=\"button\" onclick=\"mv(this,-1)\">▲</button>")
                    .append("<button type=\"button\" onclick=\"mv(this,1)\">▼</button></span></li>");
        }
        b.append("</ul>");
        b.append("<form method=\"post\" action=\"/tiles\" onsubmit=\"return pack()\">")
                .append("<input type=\"hidden\" name=\"tiles\" id=\"tl\"><button type=\"submit\">Сохранить плитки</button></form>");
        b.append("<form method=\"post\" action=\"/tiles-reset\"><button type=\"submit\" class=\"sec\">Вернуть стандартные (Okko, ivi, YouTube)</button></form>");
        b.append("<script>"
                + "function mv(b,d){var li=b.parentNode.parentNode,ul=li.parentNode;"
                + "if(d<0&&li.previousElementSibling)ul.insertBefore(li,li.previousElementSibling);"
                + "if(d>0&&li.nextElementSibling)ul.insertBefore(li.nextElementSibling,li);}"
                + "function pack(){var r=[],l=document.querySelectorAll('#ap li');"
                + "for(var i=0;i<l.length;i++){if(l[i].querySelector('input').checked)r.push(l[i].getAttribute('data-p'));}"
                + "document.getElementById('tl').value=r.join(',');return true;}"
                + "</script>");
        return b.toString();
    }

    private static String tilesDonePage(String message) {
        return "<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Готово</title>" + STYLE
                + "</head><body><div class=\"w\"><h1>Готово</h1><p>" + esc(message) + "</p>"
                + "<p><a href=\"/\" style=\"color:#B87333\">Назад к настройке</a></p></div></body></html>";
    }

    private static String donePage() {
        return "<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Готово</title>" + STYLE
                + "</head><body><div class=\"w\"><h1>Готово</h1><p>Ссылка сохранена. Экран на телевизоре сейчас откроется сам.</p></div></body></html>";
    }
}
