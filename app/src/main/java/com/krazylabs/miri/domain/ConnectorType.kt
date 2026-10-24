package com.krazylabs.miri.domain

/**
 * One connector kind, after a raw VHAL value or a Java [android.car.EvConnectorType]
 * value has been labeled for the debug probe. The card's DC combo sentence uses
 * [ConnectorCodes.DC_COMBO] only.
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
 * [android.car.hardware.property.EvChargingConnectorType], the raw values
 * [android.car.hardware.property.CarPropertyManager.getIntArrayProperty] returns
 * for [android.car.VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE].
 *
 * 1 [IEC_TYPE_1_AC], 2 [IEC_TYPE_2_AC], and 3 [IEC_TYPE_3_AC] are AC.
 * 4 [IEC_TYPE_4_DC] is CHAdeMO.
 * 5 [IEC_TYPE_1_CCS_DC] is CCS1.
 * 6 [IEC_TYPE_2_CCS_DC] is CCS2.
 * 7 [TESLA_ROADSTER].
 * DC combo is [DC_COMBO], exactly {5, 6}.
 *
 * [JAVA_UNKNOWN] and the other `JAVA_*` constants are [android.car.EvConnectorType].
 * Only [android.car.CarInfoManager.getEvConnectorTypes] returns that table, and
 * only the debug probe prints it. Those numbers are not the combo decision.
 */
object ConnectorCodes {
    /** EvChargingConnectorType.UNKNOWN. */
    const val UNKNOWN = 0

    /** EvChargingConnectorType.IEC_TYPE_1_AC. AC. */
    const val IEC_TYPE_1_AC = 1

    /** EvChargingConnectorType.IEC_TYPE_2_AC. AC. */
    const val IEC_TYPE_2_AC = 2

    /** EvChargingConnectorType.IEC_TYPE_3_AC. AC. */
    const val IEC_TYPE_3_AC = 3

    /** EvChargingConnectorType.IEC_TYPE_4_DC. CHAdeMO. */
    const val IEC_TYPE_4_DC = 4

    /** EvChargingConnectorType.IEC_TYPE_1_CCS_DC. CCS1. */
    const val IEC_TYPE_1_CCS_DC = 5

    /** EvChargingConnectorType.IEC_TYPE_2_CCS_DC. CCS2. */
    const val IEC_TYPE_2_CCS_DC = 6

    /** EvChargingConnectorType.TESLA_ROADSTER. */
    const val TESLA_ROADSTER = 7

    /** EvChargingConnectorType.TESLA_HPWC. */
    const val TESLA_HPWC = 8

    /** EvChargingConnectorType.TESLA_SUPERCHARGER. */
    const val TESLA_SUPERCHARGER = 9

    /** EvChargingConnectorType.GBT_AC. */
    const val GBT_AC = 10

    /** EvChargingConnectorType.GBT_DC. */
    const val GBT_DC = 11

    /** EvChargingConnectorType.OTHER. */
    const val OTHER = 101

    /** CCS1 and CCS2. The only raw values that add the DC combo sentence. */
    val DC_COMBO: Set<Int> = setOf(IEC_TYPE_1_CCS_DC, IEC_TYPE_2_CCS_DC)

    /** [android.car.EvConnectorType.UNKNOWN]. Debug probe only. */
    const val JAVA_UNKNOWN = 0

    /** Java J1772. Debug probe only. */
    const val JAVA_J1772 = 1

    /** Java MENNEKES. Debug probe only. */
    const val JAVA_MENNEKES = 2

    /** Java CHADEMO. Debug probe only. Raw 3 is IEC_TYPE_3_AC. */
    const val JAVA_CHADEMO = 3

    /** Java COMBO_1. Debug probe only. Raw 4 is CHAdeMO. */
    const val JAVA_COMBO_1 = 4

    /** Java COMBO_2. Debug probe only. Raw 5 is CCS1. */
    const val JAVA_COMBO_2 = 5

    /** Java TESLA_ROADSTER. Debug probe only. Raw 6 is CCS2. */
    const val JAVA_TESLA_ROADSTER = 6

    /** Java TESLA_HPWC. Debug probe only. */
    const val JAVA_TESLA_HPWC = 7

    /** Java TESLA_SUPERCHARGER. Debug probe only. */
    const val JAVA_TESLA_SUPERCHARGER = 8

    /** Java GBT. Debug probe only. */
    const val JAVA_GBT = 9

    /** Java GBT_DC. Debug probe only. */
    const val JAVA_GBT_DC = 10

    /** Java SCAME. Debug probe only. */
    const val JAVA_SCAME = 11

    /** Java OTHER. Debug probe only. */
    const val JAVA_OTHER = 101

    fun dcCombo(codes: IntArray): Boolean = codes.any { it in DC_COMBO }

    fun fromVhal(code: Int): ConnectorKind = when (code) {
        IEC_TYPE_1_AC, IEC_TYPE_2_AC, IEC_TYPE_3_AC -> ConnectorKind.AC
        IEC_TYPE_4_DC -> ConnectorKind.CHADEMO
        IEC_TYPE_1_CCS_DC -> ConnectorKind.CCS1
        IEC_TYPE_2_CCS_DC -> ConnectorKind.CCS2
        TESLA_ROADSTER, TESLA_HPWC, TESLA_SUPERCHARGER -> ConnectorKind.TESLA
        GBT_AC, GBT_DC, OTHER -> ConnectorKind.OTHER
        else -> ConnectorKind.UNKNOWN
    }

    /** Labels [android.car.CarInfoManager.getEvConnectorTypes] for the debug log. */
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
