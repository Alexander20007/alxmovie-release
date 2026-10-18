package com.alxmovies.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class AssistantTitle(val title: String, val year: String, val type: String)
data class AssistantAnswer(val reply: String, val titles: List<AssistantTitle>, val channels: List<String>, val rooms: List<String>)

/** Laura IA: pergunta ao Gemini (pela função do Supabase) e entende a resposta em JSON. */
object Assistant {
    private const val GEMINI_URL = "https://onqxflwfkexitipylixc.supabase.co/functions/v1/smooth-endpoint"
    private const val GEMINI_MODEL = "gemini-3.5-flash"

    private suspend fun askGemini(prompt: String): String {
        if (prompt.isBlank()) throw ApiException("Digite uma pergunta para a IA.")
        val body = buildJsonObject { put("prompt", prompt); put("model", GEMINI_MODEL) }.toString()
        val (code, text) = try {
            Api.postRaw(GEMINI_URL, body, 60)
        } catch (e: ApiException) {
            throw ApiException("Não foi possível falar com a IA agora. Verifique sua conexão e tente novamente.")
        }
        val data = runCatching { Api.json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw ApiException("Resposta inválida da IA.")
        if (code !in 200..299) {
            val err = data["error"]
            val msg = runCatching { err?.jsonPrimitive?.contentOrNull }.getOrNull()
                ?: runCatching { (err as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
            throw ApiException(
                msg ?: when (code) {
                    403 -> "Acesso negado: este assistente só funciona no site oficial do ALXmovies."
                    401 -> "Não autorizado a usar a IA."
                    else -> "Erro ao consultar a IA (status $code)."
                }
            )
        }
        val candidate = (data["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
        if (candidate == null && data["promptFeedback"]?.jsonObject?.get("blockReason") != null) throw ApiException("A IA não pôde responder a essa pergunta.")
        val out = (candidate?.get("content")?.jsonObject?.get("parts") as? JsonArray)
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }?.trim() ?: ""
        if (out.isEmpty()) throw ApiException("A IA não retornou nenhuma resposta. Tente reformular a pergunta.")
        return out
    }

    suspend fun ask(
        question: String,
        onlineChannels: List<Channel>,
        watchHistory: List<Pair<String, String>>,
        onlineRooms: List<RoomRow>,
    ): AssistantAnswer {
        val channelsContext = if (onlineChannels.isNotEmpty())
            "\n\nCanais de TV ao vivo confirmados ONLINE agora no ALXmovies (essa lista é real e atualizada — NUNCA cite um canal que não esteja nela, e NUNCA invente um canal):\n" +
                onlineChannels.joinToString("\n") { c -> "- ${c.name}${if (!c.group.isNullOrEmpty()) " (${c.group.replace("Canais | ", "")})" else ""}" }
        else ""
        val historyContext = if (watchHistory.isNotEmpty())
            "\n\nHistórico real deste perfil no ALXmovies (o que a pessoa já assistiu ou favoritou — use isso só como contexto de gosto/preferência, e evite recomendar de novo um título que já está nessa lista, a menos que a pessoa peça especificamente por ele):\n" +
                watchHistory.joinToString("\n") { (t, type) -> "- $t (${if (type == "tv") "série" else "filme"})" }
        else ""
        val roomsContext = if (onlineRooms.isNotEmpty())
            "\n\nSalas de \"assistir junto\" ATIVAS agora no ALXmovies (informação pública real e atualizada — NUNCA cite uma sala que não esteja nessa lista, e NUNCA invente uma):\n" +
                onlineRooms.joinToString("\n") { r -> "- \"${r.title}\" — assistindo \"${r.movieTitle}\", anfitrião: ${r.hostName}${if (r.hasPassword == true) " (privada, pede senha)" else " (pública)"}" }
        else ""

        val prompt =
            "Você é a Laura, a assistente de IA do ALXmovies (o nome que aparece para a pessoa é \"Laura IA\"), um site de streaming de filmes, séries e canais de TV ao vivo, " +
                "que também tem \"Salas\" (assistir um filme/série junto com outras pessoas ao mesmo tempo, com chat). " +
                "Responda SEMPRE em português do Brasil, de forma breve, amigável e direta.\n\n" +
                "Sua resposta deve ser APENAS um JSON válido (sem markdown, sem texto antes ou depois, sem crases), exatamente neste formato:\n" +
                "{\"reply\": \"texto da resposta para o usuário\", \"titles\": [{\"title\": \"nome real do filme/série\", \"year\": \"AAAA ou vazio\", \"type\": \"movie ou tv\"}], \"channels\": [\"nome exato do canal, se algum foi citado\"], \"rooms\": [\"nome exato da sala, se alguma foi citada\"]}\n\n" +
                "Regras:\n" +
                "- \"reply\": curto e direto — 1 frase apresentando a indicação (ex: \"Encontrei essas opções pra você:\" ou \"Baseado no que você curtiu, essa aqui deve fazer seu estilo:\"). NUNCA repita nome do filme, ano, gênero, nota ou sinopse dentro de \"reply\" — tudo isso já aparece sozinho no card de cada título logo abaixo da mensagem, então repetir deixa a resposta enorme e redundante. Só escreva uma resposta mais longa (sem essa regra de tamanho) quando a pergunta não resultar em nenhum \"titles\" — aí sim pode explicar, comentar ou conversar normalmente.\n" +
                "- \"titles\": inclua CADA filme ou série que você citar na resposta, uma vez cada, com o título original/real (para ser buscável), o ano de lançamento se souber, e \"type\" = \"movie\" para filme ou \"tv\" para série.\n" +
                "- Se o usuário pedir um título específico (ex: \"quero assistir Interestelar\"), inclua esse título em \"titles\" também, mesmo que você só confirme que encontrou.\n" +
                "- \"channels\": só existe se a pergunta for sobre canal de TV ao vivo. Cite SOMENTE nomes que apareçam exatamente na lista de canais online fornecida abaixo (se houver). Se a pergunta for sobre canais mas a lista abaixo estiver vazia ou não tiver nada relacionado, diga isso educadamente na \"reply\" (ex: \"não encontrei canais desse tipo online agora\") e deixe \"channels\" vazio — nunca invente.\n" +
                "- \"rooms\": só existe se a pergunta for sobre salas de assistir junto (ex: \"tem alguma sala aberta\", \"quero assistir com outras pessoas\", \"que salas tem agora\"). Cite SOMENTE nomes de sala que apareçam exatamente na lista de salas ativas fornecida abaixo (se houver). Se não houver nenhuma sala ativa, diga isso educadamente e sugira criar uma nova sala em \"Salas\" no menu — nunca invente uma sala.\n" +
                "- Se a pergunta não tiver nada a ver com filmes, séries, canais, salas ou entretenimento, responda educadamente nesse mesmo formato JSON, com \"titles\", \"channels\" e \"rooms\" como array vazio.\n" +
                "- IMPORTANTE — sinopses e resumos: sempre que for descrever a história de um filme/série (resumo, sinopse, \"do que se trata\"), dê uma versão SEM SPOILER por padrão — não revele o final, reviravoltas importantes da trama, nem quem morre. Só inclua esse tipo de detalhe se a pessoa pedir explicitamente (\"com spoiler\", \"conta o final\", \"pode estragar\", \"já assisti, pode falar tudo\")." +
                channelsContext + historyContext + roomsContext + "\n\nPergunta do usuário: $question"

        return parse(askGemini(prompt))
    }

    private fun parse(raw: String): AssistantAnswer {
        var text = raw.trim()
        Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE).find(text)?.let { text = it.groupValues[1].trim() }
        return try {
            val p = Api.json.parseToJsonElement(text).jsonObject
            fun strings(k: String) = (p[k] as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull?.trim() }.getOrNull() }?.filter { it.isNotEmpty() } ?: emptyList()
            val titles = (p["titles"] as? JsonArray)?.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val t = o["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                AssistantTitle(t, o["year"]?.jsonPrimitive?.contentOrNull?.trim() ?: "", if (o["type"]?.jsonPrimitive?.contentOrNull == "tv") "tv" else "movie")
            } ?: emptyList()
            val reply = p["reply"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: raw
            AssistantAnswer(reply, titles, strings("channels"), strings("rooms"))
        } catch (e: Exception) {
            AssistantAnswer(raw, emptyList(), emptyList(), emptyList())
        }
    }
}
