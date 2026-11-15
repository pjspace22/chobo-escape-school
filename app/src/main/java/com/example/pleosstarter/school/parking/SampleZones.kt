package com.example.pleosstarter.school.parking

/**
 * 초안용 오프라인 샘플 데이터.
 *
 * ⚠️ 실제 공공데이터가 아니다. 시연 흐름을 돌리려고 서울 시청 근처와
 * 에뮬레이터 내비 고정 위치(성남 수정구 시흥동) 근처에 손으로 찍은 점이다.
 * 나중에 서울 버스정류소(OA-15067)·소방용수(OA-21306)·자치구 CCTV·전국 표준데이터
 * (횡단보도·어린이보호구역·주차장)를 줄인 파일로 바꾼다. 출처: 기획서 §7.
 *
 * 시연 좌표 (adb emu geo fix 는 "경도 위도" 순서):
 *  🔴 버스정류장  adb emu geo fix 126.97800 37.56650
 *  🟢 일반 도로   adb emu geo fix 126.98400 37.57200
 *  🔴/🟠 어린이보호구역 (평일 08~20시 🔴)  adb emu geo fix 126.97000 37.56000
 *  🟠 단속 카메라  adb emu geo fix 126.98500 37.56900
 *  🔴 소화전      adb emu geo fix 126.97500 37.56500
 *  ⚪ 데이터 없음  adb emu geo fix 129.07500 35.18000 (부산)
 */
object SampleZones {

    val regions = listOf(
        Region("서울", 37.42, 37.70, 126.76, 127.19),
        Region("성남 수정구", 37.40, 37.47, 127.07, 127.17),
    )

    val zones = listOf(
        // 서울 시청 근처 (시연용)
        Zone(ZoneType.BUS_STOP, "시청앞 정류소 (샘플)", 37.56650, 126.97800),
        Zone(ZoneType.HYDRANT, "소공로 소화전 (샘플)", 37.56500, 126.97500),
        Zone(ZoneType.CROSSWALK, "을지로 횡단보도 (샘플)", 37.56600, 126.98000),
        Zone(ZoneType.SCHOOL, "샘플 초등학교", 37.56000, 126.97000),
        Zone(ZoneType.CCTV, "명동 단속 카메라 (샘플)", 37.56900, 126.98500),
        // 에뮬레이터 내비 고정 위치(37.4127212, 127.094681) 바로 옆 → FusedLocation이 없을 때 시연용
        Zone(ZoneType.BUS_STOP, "시흥동 정류소 (샘플)", 37.41280, 127.09475),
    )

    val parkingLots = listOf(
        ParkingLot("시청 공영주차장 (샘플)", 37.56830, 126.97680),
        ParkingLot("서소문 공영주차장 (샘플)", 37.56250, 126.97250),
        ParkingLot("명동 공영주차장 (샘플)", 37.57050, 126.98700),
        ParkingLot("시흥동 공영주차장 (샘플)", 37.41450, 127.09650),
    )
}
