package com.example.pleosstarter.school.parking

/**
 * 금지구역·주차장 데이터 모양.
 *
 * 반경은 기획서 plan-idea-parking-alert.md §5 판정 규칙 초안 값.
 * 법 기준 거리에 GPS 오차 여유를 더했다 (숫자는 실제 데이터 좌표를 보고 조정).
 */
enum class ZoneType(val label: String, val radiusM: Double, val priority: Int) {
    // priority: 작을수록 먼저 말한다 (과태료 큰 순)
    SCHOOL("어린이보호구역", 300.0, 0),
    HYDRANT("소화전", 8.0, 1),
    BUS_STOP("버스정류장", 15.0, 2),
    CROSSWALK("횡단보도", 10.0, 2),
    CCTV("주정차 단속 카메라", 50.0, 3),
}

data class Zone(
    val type: ZoneType,
    val name: String,
    val lat: Double,
    val lng: Double,
)

data class ParkingLot(
    val name: String,
    val lat: Double,
    val lng: Double,
)

/** 데이터가 들어 있는 지역 (위경도 네모). 밖이면 "이 지역은 아직 정보가 없어요" */
data class Region(
    val name: String,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
) {
    fun contains(lat: Double, lng: Double) = lat in minLat..maxLat && lng in minLng..maxLng
}
