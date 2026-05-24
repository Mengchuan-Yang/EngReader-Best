package com.engreader.app.storage

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * Explicitly inspects EPUB structure: mimetype, container.xml, OPF path.
 * Does NOT depend on epub4j — operates directly on the ZIP file.
 *
 * P0: The import flow must reject non-EPUB files before they enter the library.
 */
class EpubStructureInspector(private val context: Context) {

  data class ContainerInfo(
    val opfPath: String,
    val opfBaseDir: String,
    val rootfileMediaType: String,
    val isValid: Boolean = true,
    val errors: List<String> = emptyList(),
  ) {
    companion object {
      fun invalid(errors: List<String>) = ContainerInfo(
        opfPath = "", opfBaseDir = "", rootfileMediaType = "",
        isValid = false, errors = errors,
      )
    }
  }

  data class ValidationResult(
    val isValid: Boolean,
    val mimetypeOk: Boolean,
    val containerOk: Boolean,
    val opfExists: Boolean,
    val containerInfo: ContainerInfo?,
    val errors: List<String>,
  )

  data class OpfInfo(
    val opfPath: String,
    val opfBaseDir: String,
    val title: String?,
    val creator: String?,
    val manifest: List<ManifestItem>,
    val spine: List<SpineItemRef>,
  )

  data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: String? = null,
  )

  data class SpineItemRef(
    val idref: String,
    val linear: Boolean = true,
  )

  /**
   * Validate an EPUB file at the given URI.
   * Opens as ZipFile (requires a local file path — content:// URIs must be copied first).
   */
  fun validate(file: File): ValidationResult {
    val errors = mutableListOf<String>()

    if (!file.exists()) {
      return ValidationResult(false, false, false, false, null, listOf("File not found: ${file.absolutePath}"))
    }

    val zip = runCatching { ZipFile(file) }.getOrElse { e ->
      return ValidationResult(false, false, false, false, null, listOf("Not a valid ZIP: ${e.message}"))
    }

    zip.use { zf ->
      // 1. Check mimetype
      val mimetypeOk = checkMimetype(zf, errors)

      // 2. Check container.xml
      val containerInfo = checkContainer(zf, errors)
      val containerOk = containerInfo?.isValid == true

      // 3. Check OPF exists
      val opfExists = if (containerInfo != null) {
        val opfEntry = zf.getEntry(containerInfo.opfPath)
        val exists = opfEntry != null
        if (!exists) errors.add("OPF not found in ZIP: ${containerInfo.opfPath}")
        exists
      } else false

      return ValidationResult(
        isValid = mimetypeOk && containerOk && opfExists,
        mimetypeOk = mimetypeOk,
        containerOk = containerOk,
        opfExists = opfExists,
        containerInfo = containerInfo,
        errors = errors,
      )
    }
  }

  private fun checkMimetype(zip: ZipFile, errors: MutableList<String>): Boolean {
    val entry = zip.getEntry("mimetype") ?: zip.getEntry("MIMETYPE")
    if (entry == null) {
      errors.add("Missing required 'mimetype' file")
      return false
    }
    val content = runCatching {
      zip.getInputStream(entry).bufferedReader().readText().trim()
    }.getOrElse { e ->
      errors.add("Cannot read mimetype: ${e.message}")
      return false
    }
    if (content != "application/epub+zip") {
      errors.add("Invalid mimetype: '$content' (expected 'application/epub+zip')")
      return false
    }
    return true
  }

  private data class Rootfile(
    val fullPath: String,
    val mediaType: String,
  )

  private fun checkContainer(zip: ZipFile, errors: MutableList<String>): ContainerInfo? {
    val containerPath = "META-INF/container.xml"
    val entry = zip.getEntry(containerPath)
    if (entry == null) {
      errors.add("Missing required '$containerPath'")
      return null
    }

    val xmlBytes = runCatching {
      zip.getInputStream(entry).use { it.readBytes() }
    }.getOrElse { e ->
      errors.add("Cannot read $containerPath: ${e.message}")
      return null
    }

    val rootfiles = parseRootfiles(String(xmlBytes, Charsets.UTF_8))
    if (rootfiles.isEmpty()) {
      errors.add("No <rootfile> found in $containerPath")
      return null
    }

    val best = rootfiles.firstOrNull { it.mediaType == "application/oebps-package+xml" }
      ?: rootfiles.first()

    // Path normalization
    var opfPath = best.fullPath.trimStart('/')
    opfPath = runCatching { URLDecoder.decode(opfPath, "UTF-8") }.getOrDefault(opfPath)
    val opfBaseDir = opfPath.substringBeforeLast('/').let { if (it == opfPath) "" else it }

    return ContainerInfo(
      opfPath = opfPath,
      opfBaseDir = opfBaseDir,
      rootfileMediaType = best.mediaType,
    )
  }

  private fun parseRootfiles(xml: String): List<Rootfile> {
    val result = mutableListOf<Rootfile>()
    return runCatching {
      val factory = XmlPullParserFactory.newInstance()
      factory.isNamespaceAware = true
      val parser = factory.newPullParser()
      parser.setInput(StringReader(xml))

      var eventType = parser.eventType
      while (eventType != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
          val fullPath = parser.getAttributeValue(null, "full-path") ?: ""
          val mediaType = parser.getAttributeValue(null, "media-type")
            ?: "application/oebps-package+xml"
          if (fullPath.isNotBlank()) {
            result += Rootfile(fullPath = fullPath, mediaType = mediaType)
          }
        }
        eventType = parser.next()
      }
      result
    }.getOrDefault(result)
  }

  /**
   * Read container.xml from an already-imported file and return OPF info.
   * Used to provide OPF base path for resource resolution.
   */
  fun readContainer(file: File): ContainerInfo? {
    return runCatching {
      ZipFile(file).use { zf -> checkContainer(zf, mutableListOf()) }
    }.getOrNull()
  }

  /**
   * Read OPF metadata, manifest, and spine from the EPUB file.
   * Uses container.xml to locate the OPF, then parses it with XML parser.
   */
  fun readOpfInfo(file: File): OpfInfo? {
    return runCatching {
      val containerInfo = readContainer(file) ?: return null
      ZipFile(file).use { zf ->
        val opfEntry = zf.getEntry(containerInfo.opfPath) ?: return null
        val opfBytes = zf.getInputStream(opfEntry).use { it.readBytes() }
        parseOpf(String(opfBytes, Charsets.UTF_8), containerInfo)
      }
    }.getOrNull()
  }

  private fun parseOpf(xml: String, containerInfo: ContainerInfo): OpfInfo {
    val manifest = mutableListOf<ManifestItem>()
    val spine = mutableListOf<SpineItemRef>()
    var title: String? = null
    var creator: String? = null
    var inMetadata = false
    var inManifest = false
    var inSpine = false
    var currentTag: String? = null

    return runCatching {
      val factory = XmlPullParserFactory.newInstance()
      factory.isNamespaceAware = true
      val parser = factory.newPullParser()
      parser.setInput(StringReader(xml))

      var eventType = parser.eventType
      while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
          XmlPullParser.START_TAG -> {
            currentTag = parser.name
            when {
              parser.name == "metadata" -> inMetadata = true
              parser.name == "manifest" -> inManifest = true
              parser.name == "spine" -> inSpine = true
              inManifest && parser.name == "item" -> {
                manifest += ManifestItem(
                  id = parser.getAttributeValue(null, "id") ?: "",
                  href = parser.getAttributeValue(null, "href") ?: "",
                  mediaType = parser.getAttributeValue(null, "media-type") ?: "",
                  properties = parser.getAttributeValue(null, "properties"),
                )
              }
              inSpine && parser.name == "itemref" -> {
                val idref = parser.getAttributeValue(null, "idref") ?: ""
                val linear = parser.getAttributeValue(null, "linear")?.lowercase() != "no"
                spine += SpineItemRef(idref = idref, linear = linear)
              }
            }
          }
          XmlPullParser.END_TAG -> {
            when (parser.name) {
              "metadata" -> inMetadata = false
              "manifest" -> inManifest = false
              "spine" -> inSpine = false
            }
          }
          XmlPullParser.TEXT -> {
            if (inMetadata && currentTag != null) {
              when (currentTag) {
                "dc:title" -> if (title == null) title = parser.text?.trim()
                "dc:creator" -> if (creator == null) creator = parser.text?.trim()
              }
            }
          }
        }
        eventType = parser.next()
      }
      OpfInfo(
        opfPath = containerInfo.opfPath,
        opfBaseDir = containerInfo.opfBaseDir,
        title = title?.takeIf { it.isNotBlank() },
        creator = creator?.takeIf { it.isNotBlank() },
        manifest = manifest,
        spine = spine,
      )
    }.getOrDefault(OpfInfo(containerInfo.opfPath, containerInfo.opfBaseDir, null, null, manifest, spine))
  }
}
