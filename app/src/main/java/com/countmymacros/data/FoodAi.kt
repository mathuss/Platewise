package com.countmymacros.data

import androidx.annotation.StringRes
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.countmymacros.R
import org.json.JSONObject
import java.time.Duration
import java.util.Base64

data class AiItem(
    val name: String,
    val grams: Double,
    val macros: Macros,
    val confidence: String,
)

data class AiMeal(val items: List<AiItem>, val notes: String)

data class AiLabel(
    val name: String,
    val brand: String,
    val servingName: String,
    val servingGrams: Double,
    val per100: Macros,
    val notes: String,
)

/** A failure to show the user: a string resource plus an optional format argument. */
class AiException(@StringRes val messageRes: Int, val arg: Any? = null) : Exception("AI error $messageRes")

/**
 * Claude-backed replacements for Fitia's server-side scanner (`/api/v2/scan/photo`),
 * text recognition (`/api/v1/text`) and nutrition-label scan. Fitia sends the photo to its
 * own backend; here the user's own API key talks to Claude directly. Calls block: run them
 * off the main thread. [lang] ("es" / "en") is the language for food names and notes.
 */
class FoodAi(apiKey: String, baseUrl: String? = null) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .timeout(Duration.ofSeconds(120))
        .build()

    fun estimatePhoto(jpeg: ByteArray, note: String = "", lang: String = "es"): AiMeal {
        val text = if (note.isBlank()) "Estimate the foods in this photo."
        else "Estimate the foods in this photo. User's note: $note"
        return parseMeal(ask(mealSystem(lang), MEAL_SCHEMA, listOf(image(jpeg), text(text))))
    }

    fun estimateText(description: String, lang: String = "es"): AiMeal =
        parseMeal(ask(mealSystem(lang) + TEXT_ADDENDUM, MEAL_SCHEMA, listOf(text("What I ate: $description"))))

    fun readLabel(jpeg: ByteArray, lang: String = "es"): AiLabel =
        parseLabel(ask(labelSystem(lang), LABEL_SCHEMA, listOf(image(jpeg), text("Read this nutrition label."))))

    private fun ask(system: String, schema: Map<String, Any>, content: List<ContentBlockParam>): String {
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(system)
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.MEDIUM)
                    .format(
                        JsonOutputFormat.builder()
                            .schema(
                                JsonOutputFormat.Schema.builder()
                                    .putAllAdditionalProperties(schema.mapValues { JsonValue.from(it.value) })
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            // If a safety classifier declines, retry server-side on Anthropic's recommended model.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addUserMessageOfBlockParams(content)
            .build()

        val message = try {
            client.messages().create(params)
        } catch (e: UnauthorizedException) {
            throw AiException(R.string.ai_err_key)
        } catch (e: PermissionDeniedException) {
            throw AiException(R.string.ai_err_permission)
        } catch (e: RateLimitException) {
            throw AiException(R.string.ai_err_rate)
        } catch (e: BadRequestException) {
            throw AiException(R.string.ai_err_rejected, e.message ?: "")
        } catch (e: AnthropicServiceException) {
            throw if (e.statusCode() == 402) AiException(R.string.ai_err_credit)
            else AiException(R.string.ai_err_status, e.statusCode())
        } catch (e: AnthropicIoException) {
            throw AiException(R.string.offline)
        }

        when (message.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw AiException(R.string.ai_err_refusal)
            StopReason.MAX_TOKENS -> throw AiException(R.string.ai_err_truncated)
            else -> Unit
        }
        // After a server-side fallback the content can hold a discarded partial answer before
        // the final one, so take the last text block that is valid JSON.
        val texts = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }
        return texts.lastOrNull { runCatching { JSONObject(it) }.isSuccess }
            ?: throw AiException(R.string.ai_err_unexpected)
    }

    private fun image(jpeg: ByteArray) = ContentBlockParam.ofImage(
        ImageBlockParam.builder()
            .source(
                Base64ImageSource.builder()
                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                    .data(Base64.getEncoder().encodeToString(jpeg))
                    .build()
            )
            .build()
    )

    private fun text(s: String) = ContentBlockParam.ofText(TextBlockParam.builder().text(s).build())

    companion object {
        const val MODEL = "claude-opus-5-5"

        private fun languageName(lang: String) = if (lang == "es") "Spanish" else "English"

        private fun naming(lang: String) = if (lang == "es") {
            "Name items in Spanish as they're called in Argentina (\"milanesa de carne\", " +
                "\"papas fritas\", \"ensalada de lechuga y tomate\"). Split a dish into components " +
                "when they're visibly separate; keep it whole when they aren't (\"empanada de " +
                "carne\", \"pizza muzzarella\")."
        } else {
            "Name items in plain English (\"breaded beef cutlet\", \"french fries\", \"lettuce " +
                "and tomato salad\"). Split a dish into components when they're visibly separate; " +
                "keep it whole when they aren't (\"beef empanada\", \"cheese pizza\")."
        }

        fun mealSystem(lang: String) = """
            |You estimate the nutrition of meals for a macro-tracking app.
            |
            |Identify each distinct food or drink and estimate the portion actually present, in grams
            |(ml for drinks, counted as grams). Judge scale from what's in frame: plate size (~26 cm),
            |cutlery, glasses, hands, packaging. Account for preparation: oil absorbed when frying,
            |sauces, dressings, melted cheese. Base kcal, protein, carbs and fat on standard
            |food-composition data (USDA, national tables) for that portion; kcal should agree with
            |the macros (≈ 4·protein + 4·carbs + 9·fat, plus 7 kcal per gram of alcohol).
            |
            |${naming(lang)} A note from the user (a weight, an ingredient, "no oil") overrides what
            |you see. Use confidence "baja" when the food or the portion is unclear, "media" when
            |reasonably sure, "alta" when clear.
            |
            |notes: one short sentence in ${languageName(lang)} for the user with the main assumption you
            |made, or an empty string. If there's no food, return no items and say so in notes.
        """.trimMargin()

        private val TEXT_ADDENDUM = """
            |
            |
            |The user describes what they ate in words instead of sending a photo. When a quantity
            |isn't given, assume a typical portion and mention it in notes.
        """.trimMargin()

        fun labelSystem(lang: String) = """
            |Read the nutrition facts label in the photo (and the package front, if visible) for a
            |macro-tracking app. Return values per 100 g (per 100 ml for liquids). Labels often list
            |values per serving and sometimes per 100 g; when only per-serving values are printed,
            |convert using the serving size. Energy in kcal (kJ ÷ 4.184 if only kJ is shown). Carbs
            |are total carbohydrate, not sugars; fat is total fat.
            |
            |name: product name as printed, otherwise a short description in ${languageName(lang)}.
            |brand: empty string if not visible. serving_name and serving_grams: the serving as
            |printed (e.g. "1 cup", 200); use "100 g" and 100 if no serving is printed. If a number
            |isn't legible, give your best estimate and say which in notes (${languageName(lang)}, one
            |short sentence, or empty).
        """.trimMargin()

        private val NUMBER = mapOf("type" to "number")
        private val STRING = mapOf("type" to "string")

        private fun obj(props: Map<String, Any>) = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to props.keys.toList(),
            "properties" to props,
        )

        val MEAL_SCHEMA: Map<String, Any> = obj(
            linkedMapOf(
                "items" to mapOf(
                    "type" to "array",
                    "items" to obj(
                        linkedMapOf(
                            "name" to STRING,
                            "grams" to NUMBER,
                            "kcal" to NUMBER,
                            "protein" to NUMBER,
                            "carbs" to NUMBER,
                            "fat" to NUMBER,
                            "confidence" to mapOf("type" to "string", "enum" to listOf("alta", "media", "baja")),
                        )
                    ),
                ),
                "notes" to STRING,
            )
        )

        val LABEL_SCHEMA: Map<String, Any> = obj(
            linkedMapOf(
                "name" to STRING,
                "brand" to STRING,
                "serving_name" to STRING,
                "serving_grams" to NUMBER,
                "kcal_100g" to NUMBER,
                "protein_100g" to NUMBER,
                "carbs_100g" to NUMBER,
                "fat_100g" to NUMBER,
                "notes" to STRING,
            )
        )

        fun parseMeal(json: String): AiMeal {
            val o = JSONObject(json)
            val arr = o.optJSONArray("items")
            val items = (0 until (arr?.length() ?: 0)).map { i ->
                val it = arr!!.getJSONObject(i)
                AiItem(
                    name = it.optString("name").ifBlank { "?" },
                    grams = it.optDouble("grams", 0.0).coerceAtLeast(0.0),
                    macros = Macros(
                        kcal = it.optDouble("kcal", 0.0).coerceAtLeast(0.0),
                        protein = it.optDouble("protein", 0.0).coerceAtLeast(0.0),
                        carbs = it.optDouble("carbs", 0.0).coerceAtLeast(0.0),
                        fat = it.optDouble("fat", 0.0).coerceAtLeast(0.0),
                    ),
                    confidence = it.optString("confidence", "media"),
                )
            }
            return AiMeal(items, o.optString("notes"))
        }

        fun parseLabel(json: String): AiLabel {
            val o = JSONObject(json)
            return AiLabel(
                name = o.optString("name"),
                brand = o.optString("brand"),
                servingName = o.optString("serving_name").ifBlank { "100 g" },
                servingGrams = o.optDouble("serving_grams", 100.0).takeIf { it > 0 } ?: 100.0,
                per100 = Macros(
                    kcal = o.optDouble("kcal_100g", 0.0).coerceAtLeast(0.0),
                    protein = o.optDouble("protein_100g", 0.0).coerceAtLeast(0.0),
                    carbs = o.optDouble("carbs_100g", 0.0).coerceAtLeast(0.0),
                    fat = o.optDouble("fat_100g", 0.0).coerceAtLeast(0.0),
                ),
                notes = o.optString("notes"),
            )
        }
    }
}
