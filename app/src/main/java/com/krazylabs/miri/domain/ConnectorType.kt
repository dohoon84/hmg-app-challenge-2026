package com.krazylabs.miri.domain

/**
 * One connector kind, after a VHAL value or a Java [android.car.EvConnectorType] value
 * has been mapped. DC combo copy is CCS1 and CCS2 only.
 */
enum class ConnectorKind {
    UNKNOWN,
    AC,
    CHADEMO,
    CCS1,
    CCS2,
    TESLA,
    OTHER,
}

/**
 * Two number spaces for the same charging inlet.
 *
 * The card reads [android.car.VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE] through
 * [android.car.hardware.property.CarPropertyManager]. On API 34 that path returns the
 * VHAL enum ([android.car.hardware.property.EvChargingConnectorType]), not
 * [android.car.EvConnectorType]. [android.car.CarInfoManager.getEvConnectorTypes]
 * is the method that converts VHAL numbers into the Java enum.
 */
object ConnectorCodes {
    /** VHAL `EvConnectorType` / [android.car.hardware.property.EvChargingConnectorType]. */
    const val VHAL_UNKNOWN = 0

    /** VHAL IEC_TYPE_1_AC. Java enum J1772 is also 1. */
    const val VHAL_IEC_TYPE_1_AC = 1

    /** VHAL IEC_TYPE_2_AC. Java enum MENNEKES is also 2. */
    const val VHAL_IEC_TYPE_2_AC = 2

    /** VHAL IEC_TYPE_3_AC (AC). Java enum 3 is CHADEMO, a different connector. */
    const val VHAL_IEC_TYPE_3_AC = 3

    /** VHAL IEC_TYPE_4_DC, CHAdeMO. Java enum 4 is COMBO_1. */
    const val VHAL_IEC_TYPE_4_DC = 4

    /** VHAL IEC_TYPE_1_CCS_DC, CCS1 / Combo 1. Java enum 5 is COMBO_2. */
    const val VHAL_IEC_TYPE_1_CCS_DC = 5

    /** VHAL IEC_TYPE_2_CCS_DC, CCS2 / Combo 2. Java enum 6 is TESLA_ROADSTER. */
    const val VHAL_IEC_TYPE_2_CCS_DC = 6

    /** VHAL TESLA_ROADSTER. Java enum TESLA_ROADSTER is 6. */
    const val VHAL_TESLA_ROADSTER = 7

    /** VHAL TESLA_HPWC. Java enum TESLA_HPWC is 7. */
    const val VHAL_TESLA_HPWC = 8

    /** VHAL TESLA_SUPERCHARGER. Java enum TESLA_SUPERCHARGER is 8. */
    const val VHAL_TESLA_SUPERCHARGER = 9

    /** VHAL GBT_AC. Java enum GBT (AC) is 9. */
    const val VHAL_GBT_AC = 10

    /** VHAL GBT_DC. Java enum GBT_DC is 10. Java enum 11 is SCAME. */
    const val VHAL_GBT_DC = 11

    /** VHAL OTHER. Java enum OTHER is also 101. */
    const val VHAL_OTHER = 101

    /** [android.car.EvConnectorType], the converted value from CarInfoManager. */
    const val JAVA_UNKNOWN = 0

    /** Java J1772, SAE J1772 / IEC type 1 AC. */
    const val JAVA_J1772 = 1

    /** Java MENNEKES, IEC type 2 AC. */
    const val JAVA_MENNEKES = 2

    /** Java CHADEMO. VHAL 3 is IEC type 3 AC, not this. */
    const val JAVA_CHADEMO = 3

    /** Java COMBO_1, CCS1. VHAL 4 is CHAdeMO. */
    const val JAVA_COMBO_1 = 4

    /** Java COMBO_2, CCS2. VHAL 5 is CCS1. */
    const val JAVA_COMBO_2 = 5

    /** Java TESLA_ROADSTER. VHAL 6 is CCS2. */
    const val JAVA_TESLA_ROADSTER = 6

    /** Java TESLA_HPWC. */
    const val JAVA_TESLA_HPWC = 7

    /** Java TESLA_SUPERCHARGER. */
    const val JAVA_TESLA_SUPERCHARGER = 8

    /** Java GBT, the AC GB/T inlet. */
    const val JAVA_GBT = 9

    /** Java GBT_DC. */
    const val JAVA_GBT_DC = 10

    /** Java SCAME, IEC type 3 AC. */
    const val JAVA_SCAME = 11

    /** Java OTHER. */
    const val JAVA_OTHER = 101

    fun fromVhal(code: Int): ConnectorKind = when (code) {
        VHAL_IEC_TYPE_1_AC, VHAL_IEC_TYPE_2_AC, VHAL_IEC_TYPE_3_AC -> ConnectorKind.AC
        VHAL_IEC_TYPE_4_DC -> ConnectorKind.CHADEMO
        VHAL_IEC_TYPE_1_CCS_DC -> ConnectorKind.CCS1
        VHAL_IEC_TYPE_2_CCS_DC -> ConnectorKind.CCS2
        VHAL_TESLA_ROADSTER, VHAL_TESLA_HPWC, VHAL_TESLA_SUPERCHARGER -> ConnectorKind.TESLA
        VHAL_GBT_AC, VHAL_GBT_DC, VHAL_OTHER -> ConnectorKind.OTHER
        else -> ConnectorKind.UNKNOWN
    }

    fun fromJava(code: Int): ConnectorKind = when (code) {
        JAVA_J1772, JAVA_MENNEKES, JAVA_SCAME -> ConnectorKind.AC
        JAVA_CHADEMO -> ConnectorKind.CHADEMO
        JAVA_COMBO_1 -> ConnectorKind.CCS1
        JAVA_COMBO_2 -> ConnectorKind.CCS2
        JAVA_TESLA_ROADSTER, JAVA_TESLA_HPWC, JAVA_TESLA_SUPERCHARGER -> ConnectorKind.TESLA
        JAVA_GBT, JAVA_GBT_DC, JAVA_OTHER -> ConnectorKind.OTHER
        else -> ConnectorKind.UNKNOWN
    }

    fun fromVhal(codes: IntArray): List<ConnectorKind> = codes.map(::fromVhal)

    fun fromJava(codes: IntArray): List<ConnectorKind> = codes.map(::fromJava)

    fun dcCombo(kinds: Iterable<ConnectorKind>): Boolean =
        kinds.any { it == ConnectorKind.CCS1 || it == ConnectorKind.CCS2 }
}

/**
 * One log line with the three connector reads side by side.
 * [propertyValues] is CarPropertyManager. [infoManagerValues] is
 * CarInfoManager.getEvConnectorTypes(), or null when that call did not return.
 * [pleos] is the Pleos SDK label for the same integers.
 */
object ConnectorProbeText {
    fun line(
        propertyId: Int,
        propertyValues: IntArray,
        infoManagerValues: IntArray?,
        pleos: String,
    ): String {
        val info = infoManagerValues?.contentToString() ?: "unavailable"
        val javaKinds = infoManagerValues?.let { kinds(ConnectorCodes.fromJava(it)) } ?: "n/a"
        return "connector probe propertyId=$propertyId " +
            "CarPropertyManager=${propertyValues.contentToString()} " +
            "vhal=${kinds(ConnectorCodes.fromVhal(propertyValues))} " +
            "CarInfoManager.getEvConnectorTypes()=$info java=$javaKinds Pleos=$pleos"
    }

    private fun kinds(values: List<ConnectorKind>): String =
        values.joinToString(prefix = "[", postfix = "]") { it.name }
}
