package io.hmg.appchallenge.foundation.probe

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.constants.CameraType
import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.RoutePointPoiType
import ai.pleos.playground.navi.constants.RoutePointSettingType
import ai.pleos.playground.navi.constants.RoutePointType
import ai.pleos.playground.navi.constants.TrafficCongestionType
import ai.pleos.playground.navi.constants.TrafficEventType
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.constants.WaypointCmdType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.ChargerOperator
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.DrivingState
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RoutePointInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.lang.reflect.Modifier

/**
 * NaviHelper 3.0.5 listener dump. Read plus the route requests this probe needs
 * to make the navigation app emit events. No bookmark is stored by the product;
 * the poll below only asks whether the callback arrives.
 */
internal class NaviSignalProbe(
    context: Context,
    private val log: ProbeLog,
    private val background: () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val gate = Any()
    private val cadence = CadenceTracker()
    private var helper: NaviHelper? = null
    private var listenerAttached = false
    private var cadencePolling = false

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            event(
                "onRouteStarted",
                mapOf(
                    "duration" to routeStartInfo.duration,
                    "distance" to routeStartInfo.distance,
                    "tollFare" to routeStartInfo.tollFare,
                    "name" to routeStartInfo.name,
                    "remainDestinationChargingValue" to routeStartInfo.remainDestinationChargingValue,
                    "dump" to routeStartInfo.toString(),
                ),
            )
        }

        override fun onRouteEnded() = event("onRouteEnded")

        override fun onRoutingCompleted() = event("onRoutingCompleted")

        override fun onReRouteEnded() = event("onReRouteEnded")

        override fun onRouteOptionChanged(routeOption: RouteOption) {
            event("onRouteOptionChanged", mapOf("routeOption" to routeOption.name))
        }

        override fun onRouteCancelled() = event("onRouteCancelled")

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            event(
                "onWaypointChanged",
                mapOf(
                    "index" to waypointChangedInfo.index.name,
                    "cmdType" to waypointChangedInfo.cmdType.name,
                    "routeStart" to routeStart(waypointChangedInfo.routeStartInfo),
                    "dump" to waypointChangedInfo.toString(),
                ),
            )
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            event(
                "onDestinationArrived",
                mapOf(
                    "duration" to destinationArrivedInfo.duration,
                    "distance" to destinationArrivedInfo.distance,
                    "name" to destinationArrivedInfo.name,
                    "address" to destinationArrivedInfo.address,
                    "dump" to destinationArrivedInfo.toString(),
                ),
            )
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            event(
                "onWayPointArrived",
                mapOf(
                    "duration" to waypointArrivedInfo.duration,
                    "distance" to waypointArrivedInfo.distance,
                    "index" to waypointArrivedInfo.index.name,
                    "name" to waypointArrivedInfo.name,
                    "address" to waypointArrivedInfo.address,
                    "dump" to waypointArrivedInfo.toString(),
                ),
            )
        }

        override fun onBookMarkChanged() = event("onBookMarkChanged")

        override fun onDestinationChanged() = event("onDestinationChanged")

        override fun onError(errorCode: NaviErrorCode) {
            event("onError", mapOf("errorCode" to errorCode.name, "dump" to errorCode.toString()))
        }

        override fun onCurrentLocationInfo(currentLocationInfo: ai.pleos.playground.navi.data.CurrentLocationInfo) {
            event(
                "onCurrentLocationInfo",
                mapOf(
                    "latitude" to currentLocationInfo.latitude,
                    "longitude" to currentLocationInfo.longitude,
                    "heading" to currentLocationInfo.heading,
                    "address" to currentLocationInfo.address,
                    "routeId" to currentLocationInfo.routeId,
                    "currentRouteIndex" to currentLocationInfo.currentRouteIndex,
                    "dump" to currentLocationInfo.toString(),
                ),
            )
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            event(
                "onRouteStateInfo",
                mapOf(
                    "termsAgreement" to routeStateInfo.termsAgreement,
                    "naviMode" to routeStateInfo.naviMode.name,
                    "isRouting" to routeStateInfo.isRouting,
                    "routeOption" to routeStateInfo.routeOption.name,
                    "remainDestinationChargingInt" to routeStateInfo.remainDestinationChargingInt,
                    "remainWaypointChargingInt" to routeStateInfo.remainWaypointChargingInt,
                    "trafficStatus" to routeStateInfo.trafficStatus.toString(),
                    "dump" to routeStateInfo.toString(),
                ),
            )
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            event("onDestinationInfo", destinationFields(destinationInfo))
            noteCadence(
                "onDestinationInfo",
                remainDistance = destinationInfo.remainDistance,
                remainTime = destinationInfo.remainTime,
                extra = mapOf("name" to destinationInfo.name),
            )
        }

        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) {
            event(
                "onWayPointInfo",
                mapOf(
                    "count" to waypointInfo.size,
                    "items" to waypointInfo.map { point ->
                        mapOf(
                            "name" to point.name,
                            "address" to point.address,
                            "latitude" to point.latitude,
                            "longitude" to point.longitude,
                            "displayLatitude" to point.displayLatitude,
                            "displayLongitude" to point.displayLongitude,
                            "remainDistance" to point.remainDistance,
                            "remainTime" to point.remainTime,
                            "poiId" to point.poiId,
                            "poiSubId" to point.poiSubId,
                            "poiProvider" to point.poiProvider,
                            "dump" to point.toString(),
                        )
                    },
                    "remainDistances" to waypointInfo.map { it.remainDistance },
                    "remainTimes" to waypointInfo.map { it.remainTime },
                ),
            )
            noteCadence(
                "onWayPointInfo",
                remainDistance = waypointInfo.firstOrNull()?.remainDistance,
                remainTime = waypointInfo.firstOrNull()?.remainTime,
                extra = mapOf(
                    "count" to waypointInfo.size,
                    "remainDistances" to waypointInfo.map { it.remainDistance },
                ),
            )
        }

        override fun onRecentDestinationInfo(recentDestinationInfo: List<RecentDestinationInfo>) {
            event(
                "onRecentDestinationInfo",
                mapOf(
                    "productNote" to "probe only; product does not read recents",
                    "count" to recentDestinationInfo.size,
                    "items" to recentDestinationInfo.map { item ->
                        mapOf(
                            "name" to item.name,
                            "address" to item.address,
                            "latitude" to item.latitude,
                            "longitude" to item.longitude,
                            "poiId" to item.poiId,
                            "poiSubId" to item.poiSubId,
                            "poiProvider" to item.poiProvider,
                            "dump" to item.toString(),
                        )
                    },
                ),
            )
        }

        override fun onBookmarkInfo(bookmarkInfo: List<BookmarkInfo>) {
            event(
                "onBookmarkInfo",
                mapOf(
                    "productNote" to "probe only; product does not read bookmarks",
                    "count" to bookmarkInfo.size,
                    "items" to bookmarkInfo.map { item ->
                        mapOf(
                            "name" to item.name,
                            "address" to item.address,
                            "category" to item.category.name,
                            "latitude" to item.latitude,
                            "longitude" to item.longitude,
                            "poiId" to item.poiId,
                            "poiSubId" to item.poiSubId,
                            "poiProvider" to item.poiProvider,
                            "dump" to item.toString(),
                        )
                    },
                ),
            )
        }

        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            event("onCurrentRoadSpeedLimit", mapOf("speedLimit" to speedLimit))
        }

        override fun onCameraAlert(cameraAlert: List<CameraAlert>) {
            event(
                "onCameraAlert",
                mapOf(
                    "count" to cameraAlert.size,
                    "items" to cameraAlert.map { item ->
                        mapOf(
                            "id" to item.id,
                            "type" to item.type.name,
                            "status" to item.status,
                            "speedLimit" to item.speedLimit,
                            "remainDistance" to item.remainDistance,
                            "sectionAvrSpeed" to item.sectionAvrSpeed,
                            "isOverSpeed" to item.isOverSpeed,
                            "isOverSectionAvrSpeed" to item.isOverSectionAvrSpeed,
                            "dump" to item.toString(),
                        )
                    },
                ),
            )
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            event(
                "onDrivingInfo",
                mapOf(
                    "destination" to drivingState(drivingInfo.destination),
                    "waypoints" to drivingInfo.waypoints.map { drivingState(it) },
                    "dump" to drivingInfo.toString(),
                ),
            )
            noteCadence(
                "onDrivingInfo",
                remainDistance = drivingInfo.destination.distance,
                remainTime = drivingInfo.destination.duration,
                extra = mapOf(
                    "distanceField" to "DrivingState.distance",
                    "timeField" to "DrivingState.duration",
                    "waypointDistances" to drivingInfo.waypoints.map { it.distance },
                ),
            )
        }

        override fun onNavigationStatus(navigationStatus: NavigationStatus) {
            event("onNavigationStatus", mapOf("status" to navigationStatus.name))
        }

        override fun onTBTInfo(tbtInfo: List<TBTInfo>) {
            event(
                "onTBTInfo",
                mapOf(
                    "count" to tbtInfo.size,
                    "items" to tbtInfo.map { item ->
                        mapOf(
                            "type" to item.type.name,
                            "typeValue" to item.type.value,
                            "distance" to item.distance,
                            "description" to item.description,
                        )
                    },
                    "dump" to tbtInfo.joinToString(prefix = "[", postfix = "]") { it.toString() },
                ),
            )
            noteCadence(
                "onTBTInfo",
                remainDistance = tbtInfo.firstOrNull()?.distance,
                remainTime = null,
                extra = mapOf(
                    "count" to tbtInfo.size,
                    "distances" to tbtInfo.map { it.distance },
                    "note" to "TBT distance is the distance to that maneuver, not the destination.",
                ),
            )
        }

        override fun onChargerOperatorInfo(chargerOperator: List<ChargerOperator>) {
            event(
                "onChargerOperatorInfo",
                mapOf(
                    "count" to chargerOperator.size,
                    "items" to chargerOperator.map { mapOf("id" to it.id, "name" to it.name) },
                ),
            )
        }

        override fun onCautionPointInfo(cautionPoint: List<CautionPoint>) {
            event(
                "onCautionPointInfo",
                mapOf(
                    "count" to cautionPoint.size,
                    "items" to cautionPoint.map { item ->
                        mapOf(
                            "id" to item.id,
                            "type" to item.type.name,
                            "remainDistance" to item.remainDistance,
                            "dump" to item.toString(),
                        )
                    },
                ),
            )
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            val points = tripInfo.summary.routePoints
            event(
                "onTripInfo",
                mapOf(
                    "distance" to tripInfo.distance,
                    "eta" to tripInfo.eta,
                    "estimatedSocAtDest" to tripInfo.estimatedSocAtDest,
                    "energyConsumption" to tripInfo.energyConsumption,
                    "trafficConditions" to tripInfo.summary.trafficConditions.map { it.toString() },
                    "routePoints" to points.map { routePoint(it) },
                    "poiTypes" to points.map { it.poiType.name }.distinct(),
                    "dump" to tripInfo.toString(),
                ),
            )
            noteCadence(
                "onTripInfo",
                remainDistance = tripInfo.distance,
                remainTime = tripInfo.eta,
                extra = mapOf(
                    "distanceField" to "TripInfo.distance",
                    "timeField" to "TripInfo.eta",
                ),
            )
        }
    }

    private val cadencePoll = object : Runnable {
        override fun run() {
            if (!cadencePolling) return
            pollRemaining()
            main.postDelayed(this, CADENCE_POLL_MS)
        }
    }

    fun start() {
        dumpEnums()
        dumpShapes()
        val created = NaviHelper(appContext)
        synchronized(gate) { helper = created }
        attach(created)
        try {
            created.initialize(
                {
                    event("initialize", mapOf("result" to "ready"))
                },
                { error ->
                    event("initialize", probeFailure(error))
                },
            )
            event("initialize", mapOf("result" to "called"))
        } catch (error: Throwable) {
            event("initialize", probeFailure(error))
        }
        logSimulationApi(created)
        event(
            "road_callbacks",
            mapOf(
                "logged" to listOf(
                    "onCameraAlert",
                    "onCautionPointInfo",
                    "onCurrentRoadSpeedLimit",
                    "onCurrentLocationInfo",
                ),
                "note" to "NaviHelper 3.0.5 has no other road-info listener methods. Camera and caution payloads are field-dumped.",
            ),
        )
    }

    fun onProcessBackground(hold: Boolean) {
        val created = synchronized(gate) { helper }
        if (hold) {
            event("process_background", mapOf("listeners" to "kept"))
            return
        }
        if (created != null) detach(created)
        event("process_background", mapOf("listeners" to "removed"))
    }

    fun onProcessForeground() {
        val created = synchronized(gate) { helper } ?: return
        attach(created)
        event("process_foreground", mapOf("listeners" to "attached"))
    }

    fun requestJejuRental() {
        requestRoute(JEJU_LAT, JEJU_LON, JEJU_NAME, "request_route_jeju")
    }

    fun requestDestination(latitude: Double, longitude: Double, name: String) {
        requestRoute(latitude, longitude, name, "request_route_adb")
    }

    fun setCadencePoll(enabled: Boolean) {
        if (cadencePolling == enabled) {
            cadenceLog("poll_mode", mapOf("enabled" to enabled, "result" to "unchanged"))
            return
        }
        cadencePolling = enabled
        main.removeCallbacks(cadencePoll)
        cadenceLog(
            "poll_mode",
            mapOf(
                "enabled" to enabled,
                "intervalMs" to CADENCE_POLL_MS,
                "calls" to listOf("getDestinationInfo", "getTBTInfo", "getWaypointInfo"),
                "note" to "No getDrivingInfo and no getTripInfo. requestTrip is not polled. Callbacks still arrive on PROBE_CADENCE.",
            ),
        )
        if (enabled) main.post(cadencePoll)
    }

    fun requestGasStationByName() {
        event(
            "gas_station_api",
            mapOf(
                "limitation" to
                    "No requestRoute-by-name and no gas-station POI type. " +
                    "SearchPlace(keyword) opens a search screen. " +
                    "requestRoute still needs coordinates; poiName is only a label. " +
                    "DestinationInfo has no category field. " +
                    "RoutePointPoiType is NORMAL or EV_CHARGING. " +
                    "DrivingState.isChargingStation is the charging flag.",
                "keyword" to GAS_NAME,
                "labelCoordinate" to "$JEJU_LAT,$JEJU_LON",
            ),
        )
        val created = synchronized(gate) { helper }
        if (created == null) {
            event("request_search_place", mapOf("result" to "not_ready"))
            return
        }
        try {
            created.requestNavigationScreen(NavigationScreenRequest.SearchPlace(GAS_NAME))
            event("request_search_place", mapOf("result" to "called", "keyword" to GAS_NAME))
        } catch (error: Throwable) {
            event("request_search_place", probeFailure(error))
        }
        requestRoute(JEJU_LAT, JEJU_LON, GAS_NAME, "request_route_gas_label")
    }

    fun trySimulationGuiding() {
        val created = synchronized(gate) { helper }
        if (created == null) {
            event("simulation_guiding", mapOf("result" to "not_ready"))
            return
        }
        val reflected = simulationMethods(created)
        if (reflected.isEmpty()) {
            event(
                "simulation_guiding",
                mapOf(
                    "result" to "no_api",
                    "enum" to NaviMode.values().map { it.name },
                    "limitation" to
                        "NaviMode.SIMULATION_GUIDING exists. NaviHelper 3.0.5 has no method " +
                        "that accepts NaviMode or starts simulation. " +
                        "naviMode is only reported on RouteStateInfo.",
                ),
            )
        } else {
            val setter = reflected.firstOrNull { method ->
                method.parameterTypes.size == 1 && method.parameterTypes[0].name.endsWith("NaviMode")
            }
            if (setter == null) {
                event(
                    "simulation_guiding",
                    mapOf(
                        "result" to "methods_found_not_invoked",
                        "methods" to reflected.map { it.toGenericString() },
                    ),
                )
            } else {
                try {
                    setter.invoke(created, NaviMode.SIMULATION_GUIDING)
                    event("simulation_guiding", mapOf("result" to "invoked", "method" to setter.name))
                } catch (error: Throwable) {
                    event("simulation_guiding", probeFailure(error))
                }
            }
        }
        call("getRouteStateInfo") { created.getRouteStateInfo() }
    }

    fun pollGetters() {
        val created = synchronized(gate) { helper }
        if (created == null) {
            event("poll", mapOf("result" to "not_ready"))
            return
        }
        call("getRouteStateInfo") { created.getRouteStateInfo() }
        call("getCurrentLocationInfo") { created.getCurrentLocationInfo() }
        call("getDestinationInfo") { created.getDestinationInfo() }
        call("getWaypointInfo") { created.getWaypointInfo() }
        call("getTBTInfo") { created.getTBTInfo() }
        call("getChargerOperatorInfo") { created.getChargerOperatorInfo() }
        call("getBookmarkInfo") { created.getBookmarkInfo() }
        call("getRecentDestinationInfo") { created.getRecentDestinationInfo() }
        call("requestTrip") {
            created.requestTrip(
                TripRequest(
                    latitude = JEJU_LAT,
                    longitude = JEJU_LON,
                    poiId = "",
                    poiName = JEJU_NAME,
                    poiSubId = "",
                ),
            )
        }
        event(
            "poll",
            mapOf(
                "note" to "There is no getTripInfo. requestTrip is the call that leads to onTripInfo. " +
                    "getBookmarkInfo and getRecentDestinationInfo are probe-only.",
            ),
        )
    }

    fun release() {
        cadencePolling = false
        main.removeCallbacks(cadencePoll)
        val created = synchronized(gate) {
            val current = helper
            helper = null
            current
        } ?: return
        detach(created)
        try {
            created.release()
            event("release", mapOf("result" to "called"))
        } catch (error: Throwable) {
            event("release", probeFailure(error))
        }
    }

    private fun requestRoute(latitude: Double, longitude: Double, name: String, eventName: String) {
        val created = synchronized(gate) { helper }
        if (created == null) {
            event(eventName, mapOf("result" to "not_ready", "name" to name))
            return
        }
        try {
            created.requestRoute(
                RouteInfo(
                    latitude = latitude,
                    longitude = longitude,
                    displayLatitude = null,
                    displayLongitude = null,
                    poiName = name,
                    poiId = "",
                    address = "",
                    poiSubId = "",
                    routeOption = RouteOption.RECOMMENDED,
                ),
            )
            event(
                eventName,
                mapOf(
                    "result" to "called",
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "poiName" to name,
                    "poiId" to "",
                    "routeOption" to RouteOption.RECOMMENDED.name,
                ),
            )
        } catch (error: Throwable) {
            event(eventName, probeFailure(error) + mapOf("poiName" to name))
        }
    }

    private fun call(name: String, block: () -> Unit) {
        try {
            block()
            event(name, mapOf("result" to "called"))
        } catch (error: Throwable) {
            event(name, probeFailure(error))
        }
    }

    private fun attach(target: NaviHelper) {
        synchronized(gate) {
            if (listenerAttached) return
            target.addListener(listener)
            listenerAttached = true
        }
        event("listener", mapOf("result" to "added"))
    }

    private fun detach(target: NaviHelper) {
        val removed = synchronized(gate) {
            if (!listenerAttached) return
            listenerAttached = false
            true
        }
        if (!removed) return
        try {
            target.removeListener(listener)
            event("listener", mapOf("result" to "removed"))
        } catch (error: Throwable) {
            event("listener", probeFailure(error))
        }
    }

    private fun dumpEnums() {
        val classes = listOf(
            TurnType::class.java,
            RoutePointPoiType::class.java,
            NaviMode::class.java,
            NavigationStatus::class.java,
            RouteOption::class.java,
            RoutePointType::class.java,
            RoutePointSettingType::class.java,
            CautionPointType::class.java,
            BookmarkType::class.java,
            NaviErrorCode::class.java,
            CameraType::class.java,
            TrafficCongestionType::class.java,
            TrafficEventType::class.java,
            WaypointCmdType::class.java,
            WaypointIndex::class.java,
        )
        classes.forEach { cls ->
            event(
                "enum_values",
                mapOf(
                    "className" to cls.name,
                    "values" to reflectEnum(cls),
                ),
            )
        }
    }

    private fun dumpShapes() {
        event(
            "sdk_shape",
            mapOf(
                "DestinationInfoGetters" to getters(DestinationInfo::class.java),
                "DrivingStateGetters" to getters(DrivingState::class.java),
                "RoutePointInfoGetters" to getters(RoutePointInfo::class.java),
                "RecentDestinationInfoGetters" to getters(RecentDestinationInfo::class.java),
                "BookmarkInfoGetters" to getters(BookmarkInfo::class.java),
                "TBTInfoGetters" to getters(TBTInfo::class.java),
                "limitation" to
                    "DestinationInfo has poiId, poiSubId, poiProvider and no category or poiType. " +
                    "isChargingStation is on DrivingState, not DestinationInfo. " +
                    "RoutePointPoiType is on RoutePointInfo (trip summary), values NORMAL and EV_CHARGING. " +
                    "BookmarkInfo.category is BookmarkType (HOME, OFFICE, EV_CHARGING_STATION, DEFAULT) and is not a destination category.",
            ),
        )
    }

    private fun logSimulationApi(target: NaviHelper) {
        val methods = simulationMethods(target).map { it.toGenericString() }
        event(
            "simulation_api",
            mapOf(
                "methods" to methods,
                "naviMode" to NaviMode.values().map { it.name },
                "limitation" to if (methods.isEmpty()) {
                    "No NaviHelper method mentions NaviMode, simulation, or mock."
                } else {
                    "See methods."
                },
            ),
        )
    }

    private fun simulationMethods(target: NaviHelper) =
        target.javaClass.methods.filter { method ->
            val blob = method.name + method.parameterTypes.joinToString { it.name }
            blob.contains("NaviMode", ignoreCase = true) ||
                blob.contains("Simulat", ignoreCase = true) ||
                blob.contains("Mock", ignoreCase = true)
        }

    private fun reflectEnum(cls: Class<*>): List<Map<String, Any?>> {
        val constants = cls.enumConstants ?: emptyArray()
        val valueMethod = cls.methods.firstOrNull { method ->
            method.name == "getValue" && method.parameterCount == 0 && Modifier.isPublic(method.modifiers)
        }
        return constants.map { item ->
            val entry = item as Enum<*>
            val row = linkedMapOf<String, Any?>(
                "name" to entry.name,
                "ordinal" to entry.ordinal,
            )
            if (valueMethod != null) {
                row["value"] = runCatching { valueMethod.invoke(item) }.getOrElse { it.message }
            }
            row
        }
    }

    private fun getters(cls: Class<*>): List<String> =
        cls.methods
            .filter { method ->
                method.parameterCount == 0 &&
                    (method.name.startsWith("get") || method.name.startsWith("is")) &&
                    method.declaringClass != Any::class.java &&
                    !method.name.contains("$")
            }
            .map { it.name }
            .distinct()
            .sorted()

    private fun destinationFields(info: DestinationInfo): Map<String, Any?> = mapOf(
        "latitude" to info.latitude,
        "longitude" to info.longitude,
        "displayLatitude" to info.displayLatitude,
        "displayLongitude" to info.displayLongitude,
        "name" to info.name,
        "address" to info.address,
        "remainDistance" to info.remainDistance,
        "remainTime" to info.remainTime,
        "tollFare" to info.tollFare,
        "poiId" to info.poiId,
        "poiSubId" to info.poiSubId,
        "poiProvider" to info.poiProvider,
        "poiType" to "ABSENT",
        "isChargingStation" to "ABSENT_ON_DestinationInfo",
        "dump" to info.toString(),
    )

    private fun drivingState(state: DrivingState): Map<String, Any?> = mapOf(
        "name" to state.name,
        "duration" to state.duration,
        "distance" to state.distance,
        "remainDestinationChargingValue" to state.remainDestinationChargingValue,
        "isChargingStation" to state.isChargingStation,
        "isRecommendedChargingStation" to state.isRecommendedChargingStation,
        "maxChargingPower" to state.maxChargingPower,
        "targetSoc" to state.targetSoc,
        "chargerCount" to state.chargerCount,
        "chargingPlan" to state.chargingPlan.toString(),
        "dump" to state.toString(),
    )

    private fun routePoint(point: RoutePointInfo): Map<String, Any?> = mapOf(
        "latitude" to point.latitude,
        "longitude" to point.longitude,
        "poiName" to point.poiName,
        "poiId" to point.poiId,
        "ratioOnRoute" to point.ratioOnRoute,
        "poiType" to point.poiType.name,
        "pointType" to point.pointType.name,
        "settingType" to point.settingType.name,
    )

    private fun routeStart(info: RouteStartInfo): Map<String, Any?> = mapOf(
        "duration" to info.duration,
        "distance" to info.distance,
        "tollFare" to info.tollFare,
        "name" to info.name,
        "remainDestinationChargingValue" to info.remainDestinationChargingValue,
    )

    private fun pollRemaining() {
        val created = synchronized(gate) { helper }
        if (created == null) {
            cadenceLog("poll_tick", mapOf("result" to "not_ready"))
            return
        }
        listOf(
            "getDestinationInfo" to { created.getDestinationInfo() },
            "getTBTInfo" to { created.getTBTInfo() },
            "getWaypointInfo" to { created.getWaypointInfo() },
        ).forEach { (name, block) ->
            try {
                block()
                cadenceLog("poll_tick", mapOf("call" to name, "result" to "called", "origin" to "poll"))
            } catch (error: Throwable) {
                cadenceLog("poll_tick", probeFailure(error) + mapOf("call" to name, "origin" to "poll"))
            }
        }
    }

    private fun noteCadence(eventName: String, remainDistance: Any?, remainTime: Any?, extra: Map<String, Any?>) {
        val elapsed = SystemClock.elapsedRealtime()
        cadenceLog(
            eventName,
            ProbeClocks.fields(elapsed) + cadence.mark(eventName, elapsed) + mapOf(
                "origin" to "callback",
                "remainDistance" to remainDistance,
                "remainTime" to remainTime,
            ) + extra,
        )
    }

    private fun cadenceLog(eventName: String, fields: Map<String, Any?>) {
        val stamped = if (fields.containsKey("elapsedRealtimeMs")) fields else ProbeClocks.fields() + fields
        log.write(TAG_CADENCE, eventName, background(), stamped)
    }

    private fun event(name: String, fields: Map<String, Any?> = emptyMap()) {
        log.write(TAG, name, background(), fields)
    }

    private companion object {
        const val TAG = "PROBE_NAVI"
        const val TAG_CADENCE = "PROBE_CADENCE"
        const val CADENCE_POLL_MS = 1_000L
        const val JEJU_LAT = 33.5059
        const val JEJU_LON = 126.4929
        const val JEJU_NAME = "제주공항 렌터카"
        const val GAS_NAME = "주유소"
    }
}
