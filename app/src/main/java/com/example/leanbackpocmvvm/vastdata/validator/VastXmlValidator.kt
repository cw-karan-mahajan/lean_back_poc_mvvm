package com.example.leanbackpocmvvm.vastdata.validator

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VastXmlValidator @Inject constructor(
    private val supportedVersions: Set<String> = setOf("2.0", "3.0", "4.0", "4.1"),
    private val requiredElements: Set<String> = setOf(
        "VAST",
        "Ad",
        "InLine",
        "Creative",
        "Linear",
        "MediaFiles"
    ),
    private val maxMediaFileSize: Long = 100 * 1024 * 1024 // 100MB
) {
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<ValidationError>) : ValidationResult()
    }

    data class ValidationError(
        val type: ErrorType,
        val message: String,
        val element: String? = null
    )

    enum class ErrorType {
        UNSUPPORTED_VERSION,
        MISSING_REQUIRED_ELEMENT,
        INVALID_MEDIA_FILE,
        MALFORMED_XML,
        EMPTY_REQUIRED_FIELD
    }

    fun validate(inputStream: InputStream): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            val foundElements = mutableSetOf<String>()
            var vastVersion: String? = null
            var mediaFileCount = 0

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val elementName = parser.name
                        foundElements.add(elementName)

                        when (elementName) {
                            "VAST" -> {
                                vastVersion = parser.getAttributeValue(null, "version")
                                validateVastVersion(vastVersion, errors)
                            }
                            "MediaFile" -> {
                                mediaFileCount++
                                validateMediaFile(parser, errors)
                            }
                            "Duration", "AdTitle", "AdSystem" -> {
                                validateRequiredField(parser, elementName, errors)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            validateRequiredElements(foundElements, errors)
            validateMediaFilePresence(mediaFileCount, errors)

        } catch (e: Exception) {
            Log.e(TAG, "Error validating VAST XML", e)
            errors.add(
                ValidationError(
                    ErrorType.MALFORMED_XML,
                    "Failed to parse XML: ${e.message}"
                )
            )
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    private fun validateVastVersion(version: String?, errors: MutableList<ValidationError>) {
        if (version == null || version !in supportedVersions) {
            errors.add(
                ValidationError(
                    ErrorType.UNSUPPORTED_VERSION,
                    "Unsupported VAST version: $version",
                    "VAST"
                )
            )
        }
    }

    private fun validateMediaFile(parser: XmlPullParser, errors: MutableList<ValidationError>) {
        val type = parser.getAttributeValue(null, "type")
        val delivery = parser.getAttributeValue(null, "delivery")
        val bitrate = parser.getAttributeValue(null, "bitrate")?.toIntOrNull()
        val width = parser.getAttributeValue(null, "width")?.toIntOrNull()
        val height = parser.getAttributeValue(null, "height")?.toIntOrNull()

        if (type == null || delivery == null || bitrate == null || width == null || height == null) {
            errors.add(
                ValidationError(
                    ErrorType.INVALID_MEDIA_FILE,
                    "MediaFile missing required attributes",
                    "MediaFile"
                )
            )
        }
    }

    private fun validateRequiredElements(
        foundElements: Set<String>,
        errors: MutableList<ValidationError>
    ) {
        requiredElements.forEach { required ->
            if (required !in foundElements) {
                errors.add(
                    ValidationError(
                        ErrorType.MISSING_REQUIRED_ELEMENT,
                        "Required element missing: $required",
                        required
                    )
                )
            }
        }
    }

    private fun validateMediaFilePresence(count: Int, errors: MutableList<ValidationError>) {
        if (count == 0) {
            errors.add(
                ValidationError(
                    ErrorType.MISSING_REQUIRED_ELEMENT,
                    "No MediaFile elements found",
                    "MediaFiles"
                )
            )
        }
    }

    private fun validateRequiredField(
        parser: XmlPullParser,
        fieldName: String,
        errors: MutableList<ValidationError>
    ) {
        val content = parser.nextText().trim()
        if (content.isEmpty()) {
            errors.add(
                ValidationError(
                    ErrorType.EMPTY_REQUIRED_FIELD,
                    "Required field is empty: $fieldName",
                    fieldName
                )
            )
        }
    }

    companion object {
        private const val TAG = "VastXmlValidator"
    }
}