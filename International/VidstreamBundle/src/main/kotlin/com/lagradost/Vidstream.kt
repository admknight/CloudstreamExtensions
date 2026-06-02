package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Vidstream(val mainUrl: String) {
    val name: String = "Vidstream"

    companion object {
        data class GogoSources(
            @JsonProperty("source") val source: List<GogoSource>?,
            @JsonProperty("sourceBk") val sourceBk: List<GogoSource>?,
        )

        data class GogoSource(
            @JsonProperty("file") val file: String,
            @JsonProperty("label") val label: String?,
            @JsonProperty("type") val type: String?,
            @JsonProperty("default") val default: String? = null
        )

        private fun cryptoHandler(
            string: String,
            iv: String,
            secretKeyString: String,
            encrypt: Boolean = true
        ): String {
            val ivParameterSpec = IvParameterSpec(iv.toByteArray())
            val secretKey = SecretKeySpec(secretKeyString.toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            return if (!encrypt) {
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
                String(cipher.doFinal(base64DecodeArray(string)))
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)
                base64Encode(cipher.doFinal(string.toByteArray()))
            }
        }

        suspend fun extractVidstream(
            iframeUrl: String,
            mainApiName: String,
            callback: (ExtractorLink) -> Unit,
            iv: String?,
            secretKey: String?,
            secretDecryptKey: String?,
            isUsingAdaptiveKeys: Boolean,
            isUsingAdaptiveData: Boolean,
            iframeDocument: Document? = null
        ) {
            runCatching {
                if ((iv == null || secretKey == null || secretDecryptKey == null) && !isUsingAdaptiveKeys) return@runCatching

                val id = Regex("id=([^&]+)").find(iframeUrl)?.groupValues?.get(1) ?: return@runCatching

                var document: Document? = iframeDocument
                val foundIv = iv ?: (document ?: app.get(iframeUrl).document.also { document = it })
                        .selectFirst("div.wrapper[class*=container]")?.attr("class")?.split("-")?.lastOrNull() ?: return@runCatching
                
                val foundKey = secretKey ?: base64Encode(base64DecodeArray(id) + foundIv.toByteArray())
                val foundDecryptKey = secretDecryptKey ?: foundKey

                val mainHost = "https://" + URI(iframeUrl).host
                val encryptedId = cryptoHandler(id, foundIv, foundKey)
                val encryptRequestData = if (isUsingAdaptiveData) {
                    val realDocument = document ?: app.get(iframeUrl).document
                    val dataEncrypted = realDocument.selectFirst("script[data-name='episode']")?.attr("data-value") ?: ""
                    val headers = cryptoHandler(dataEncrypted, foundIv, foundKey, false)
                    "id=$encryptedId&alias=$id&" + headers.substringAfter("&")
                } else {
                    "id=$encryptedId&alias=$id"
                }

                val jsonResponse = app.get("$mainHost/encrypt-ajax.php?$encryptRequestData", headers = mapOf("X-Requested-With" to "XMLHttpRequest")).text
                val dataEncrypted = jsonResponse.substringAfter("{\"data\":\"").substringBefore("\"}")
                val dataDecrypted = cryptoHandler(dataEncrypted, foundIv, foundDecryptKey, false)
                val sources = AppUtils.parseJson<GogoSources>(dataDecrypted)

                sources.source?.forEach {
                    callback.invoke(newExtractorLink(mainApiName, mainApiName, it.file, INFER_TYPE) {
                        this.referer = mainHost
                        this.quality = getQualityFromName(it.label)
                    })
                }
                sources.sourceBk?.forEach {
                    callback.invoke(newExtractorLink(mainApiName, mainApiName, it.file, INFER_TYPE) {
                        this.referer = mainHost
                        this.quality = getQualityFromName(it.label)
                    })
                }
            }
        }
    }

    private fun getExtractorUrl(id: String): String = "$mainUrl/streaming.php?id=$id"
    private fun getDownloadUrl(id: String): String = "$mainUrl/download?id=$id"
    private val normalApis = arrayListOf(MultiQuality())

    suspend fun getUrl(
        id: String,
        isCasting: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val extractorUrl = getExtractorUrl(id)
        runAllAsync(
            {
                normalApis.forEach { api ->
                    val url = api.getExtractorUrl(id)
                    api.getSafeUrl(url, callback = callback, subtitleCallback = subtitleCallback)
                }
            }, {
                val link = getDownloadUrl(id)
                val page = app.get(link, referer = extractorUrl)
                val pageDoc = page.document
                val qualityRegex = Regex("(\\d+)P")

                pageDoc.select(".dowload > a").forEach { element ->
                    val href = element.attr("href")
                    val qual = if (element.text().contains("HDP")) "1080" else qualityRegex.find(element.text())?.groupValues?.get(1) ?: ""

                    if (!loadExtractor(href, link, subtitleCallback, callback)) {
                        callback.invoke(newExtractorLink(this.name, this.name, href, INFER_TYPE) {
                            this.quality = getQualityFromName(qual)
                            this.referer = page.url
                        })
                    }
                }
            }, {
                val response = app.get(extractorUrl)
                val document = response.document
                val primaryLinks = document.select("ul.list-server-items > li.linkserver")

                primaryLinks.distinctBy { it.attr("data-video") }.forEach { element ->
                    val link = element.attr("data-video")
                    extractorApis.filter { !it.requiresReferer || !isCasting }.forEach { api ->
                        if (link.startsWith(api.mainUrl)) {
                            api.getSafeUrl(link, extractorUrl, subtitleCallback, callback)
                        }
                    }
                }
            }
        )
        return true
    }
}
