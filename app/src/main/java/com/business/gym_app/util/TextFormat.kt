package com.business.gym_app.util

/**
 * Утилиты приведения пользовательского текста новостей к аккуратному виду.
 *
 * Используются при публикации/редактировании (чтобы на сервер не уезжал «мусор»)
 * и при отображении (чтобы старые новости с лишними пробелами тоже читались нормально).
 */

/** Неразрывные, узкие, тонкие и прочие «спецпробелы», нулевой ширины символы и табуляция. */
private val EXOTIC_SPACES = Regex("[\\u00A0\\u2000-\\u200B\\u202F\\u205F\\u2060\\u3000\\t]")

/** Два и более обычных пробела подряд. */
private val MULTIPLE_SPACES = Regex(" {2,}")

/**
 * Заголовок новости: однострочный текст без лишних пробелов.
 * Переносы строк и любые пробельные символы схлопываются в один пробел.
 */
fun formatNewsTitle(raw: String): String =
    raw.replace(EXOTIC_SPACES, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Текст новости в «достойном» виде:
 * - спецпробелы (NBSP и т.п.) и табуляция заменяются обычным пробелом;
 * - `\r\n`/`\r` нормализуются в `\n`;
 * - серии пробелов внутри строки схлопываются до одного;
 * - каждая строка обрезается по краям;
 * - не более одной пустой строки подряд (абзацы), без пустых строк в начале и конце.
 */
fun formatNewsText(raw: String): String {
    val normalized = raw
        .replace(EXOTIC_SPACES, " ")
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    val lines = normalized.lines().map { it.replace(MULTIPLE_SPACES, " ").trim() }

    val result = ArrayList<String>(lines.size)
    for (line in lines) {
        if (line.isEmpty()) {
            // Пустую строку добавляем только если предыдущая не пустая
            // (не более одного пустого разделителя подряд) и что-то уже есть
            // (нет пустых строк в начале).
            if (result.isNotEmpty() && result.last().isNotEmpty()) result.add("")
        } else {
            result.add(line)
        }
    }
    // trimEnd убирает возможный хвостовой перенос строки
    return result.joinToString("\n").trimEnd()
}
