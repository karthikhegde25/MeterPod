package com.karthikhegde.meterpod.filecreator

enum class Kind { TEXT, RTF, PNG, JPEG, WEBP, BMP, GIF, WAV, ZIP, TAR, GZIP, BASE64, SQLITE, BIN, PDF, DOCX, XLSX }

/** One entry of the File Creator format list. [template] is the starter content shown in the editor. */
data class FileFormat(
    val label: String,
    val ext: String,
    val mime: String,
    val kind: Kind,
    val template: String = ""
) {
    val isImage get() = kind == Kind.PNG || kind == Kind.JPEG || kind == Kind.WEBP || kind == Kind.BMP || kind == Kind.GIF
}

object FileFormats {
    private fun t(label: String, ext: String, mime: String, template: String) =
        FileFormat(label, ext, mime, Kind.TEXT, template)

    val all: List<FileFormat> = listOf(
        t("Plain Text", "txt", "text/plain", "Hello from MeterPod!\n"),
        t("Markdown", "md", "text/markdown", "# Title\n\nSome **bold** text.\n\n- Item one\n- Item two\n"),
        t("JSON", "json", "application/json", "{\n  \"name\": \"MeterPod\",\n  \"tools\": [1, 2, 3]\n}\n"),
        t("CSV", "csv", "text/csv", "name,qty,price\nApple,3,1.5\nPear,2,2.25\n"),
        t("XML", "xml", "application/xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n  <item id=\"1\">Hello</item>\n</root>\n"),
        t("HTML", "html", "text/html", "<!DOCTYPE html>\n<html>\n<head>\n  <meta charset=\"utf-8\">\n  <title>Page</title>\n</head>\n<body>\n  <h1>Hello</h1>\n</body>\n</html>\n"),
        t("CSS", "css", "text/css", "body {\n  margin: 0;\n  font-family: sans-serif;\n}\n"),
        t("JavaScript", "js", "text/javascript", "console.log('Hello');\n"),
        t("Kotlin", "kt", "text/x-kotlin", "fun main() {\n    println(\"Hello\")\n}\n"),
        t("Java", "java", "text/x-java-source", "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}\n"),
        t("Python", "py", "text/x-python", "def main():\n    print(\"Hello\")\n\n\nif __name__ == \"__main__\":\n    main()\n"),
        t("YAML", "yaml", "application/x-yaml", "name: example\nitems:\n  - one\n  - two\n"),
        t("YAML (.yml)", "yml", "application/x-yaml", "name: example\nitems:\n  - one\n  - two\n"),
        t("TOML", "toml", "application/toml", "[package]\nname = \"example\"\nversion = \"1.0.0\"\n"),
        t("INI", "ini", "text/plain", "[section]\nkey=value\n"),
        t("Properties", "properties", "text/x-java-properties", "app.name=MeterPod\napp.debug=false\n"),
        t("SQL", "sql", "application/sql", "CREATE TABLE notes (id INTEGER PRIMARY KEY, body TEXT);\nINSERT INTO notes (body) VALUES ('Hello');\n"),
        t("Shell Script", "sh", "application/x-sh", "#!/bin/sh\necho \"Hello\"\n"),
        t("Log", "log", "text/plain", "2026-01-01 12:00:00 INFO Started\n2026-01-01 12:00:01 WARN Something happened\n"),
        t("Diff", "diff", "text/x-diff", "--- a/file.txt\n+++ b/file.txt\n@@ -1 +1 @@\n-old line\n+new line\n"),
        t("Patch", "patch", "text/x-diff", "--- a/file.txt\n+++ b/file.txt\n@@ -1 +1 @@\n-old line\n+new line\n"),
        t("Environment (.env)", "env", "text/plain", "API_URL=https://example.com\nDEBUG=false\n"),
        t("Git Ignore", "gitignore", "text/plain", "build/\n*.log\n.idea/\nlocal.properties\n"),
        t("SVG", "svg", "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"200\" viewBox=\"0 0 200 200\">\n  <circle cx=\"100\" cy=\"100\" r=\"80\" fill=\"#4C8DFF\"/>\n</svg>\n"),
        t("SubRip Subtitles", "srt", "application/x-subrip", "1\n00:00:01,000 --> 00:00:03,000\nHello!\n\n2\n00:00:04,000 --> 00:00:06,000\nSecond line.\n"),
        t("WebVTT Subtitles", "vtt", "text/vtt", "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nHello!\n"),
        FileFormat("RTF", "rtf", "application/rtf", Kind.RTF, "Hello from MeterPod!\nSecond line."),
        FileFormat("PNG Image", "png", "image/png", Kind.PNG, "MeterPod"),
        FileFormat("JPEG Image", "jpg", "image/jpeg", Kind.JPEG, "MeterPod"),
        FileFormat("WebP Image", "webp", "image/webp", Kind.WEBP, "MeterPod"),
        FileFormat("BMP Image", "bmp", "image/bmp", Kind.BMP, "MeterPod"),
        FileFormat("GIF Image", "gif", "image/gif", Kind.GIF, "MeterPod"),
        FileFormat("WAV Audio (sine tone)", "wav", "audio/wav", Kind.WAV),
        FileFormat("ZIP Archive", "zip", "application/zip", Kind.ZIP, "Hello from MeterPod!\n"),
        FileFormat("TAR Archive", "tar", "application/x-tar", Kind.TAR, "Hello from MeterPod!\n"),
        FileFormat("GZIP (compressed text)", "gz", "application/gzip", Kind.GZIP, "Hello from MeterPod!\n"),
        FileFormat("Base64-encoded text", "txt", "text/plain", Kind.BASE64, "Text to encode as Base64"),
        FileFormat("SQLite Database", "db", "application/vnd.sqlite3", Kind.SQLITE,
            "CREATE TABLE notes (id INTEGER PRIMARY KEY, body TEXT);\nINSERT INTO notes (body) VALUES ('Hello');\nINSERT INTO notes (body) VALUES ('World');"),
        FileFormat("SQLite Database (.sqlite)", "sqlite", "application/vnd.sqlite3", Kind.SQLITE,
            "CREATE TABLE notes (id INTEGER PRIMARY KEY, body TEXT);\nINSERT INTO notes (body) VALUES ('Hello');"),
        FileFormat("Raw Binary (from hex)", "bin", "application/octet-stream", Kind.BIN, "48 65 6C 6C 6F 20 4D 65 74 65 72 50 6F 64"),
        FileFormat("PDF Document", "pdf", "application/pdf", Kind.PDF, "Hello from MeterPod!\n\nThis PDF was created on your phone."),
        FileFormat("Word Document", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Kind.DOCX,
            "Hello from MeterPod!\n\nEach line becomes a paragraph."),
        FileFormat("Excel Workbook (from CSV)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Kind.XLSX,
            "name,qty,price\nApple,3,1.5\nPear,2,2.25")
    )
}
