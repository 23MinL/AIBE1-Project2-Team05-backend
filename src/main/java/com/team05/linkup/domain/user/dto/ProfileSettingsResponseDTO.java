package com.team05.linkup.domain.user.dto;

import com.team05.linkup.domain.enums.ActivityTime;
import com.team05.linkup.domain.enums.ActivityType;
import com.team05.linkup.domain.enums.Interest;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileSettingsResponseDTO {

    // 🔹 기본 정보
    private String nickname;
    private String profileImageUrl;
    private String introduction;

    // 🔹 활동 관련
    private Interest interest;
    private ActivityTime activityTime;
    private ActivityType activityType;

    // 🔹 지역 정보
    private String area;        // Area 엔티티에서 getAreaName()으로 추출
    private Integer sigungu;    // 구/군 코드

    // 🔹 태그
    private List<String> tags;  // ','로 구분된 String → List<String>으로 변환

    // 🔹 멘토 전용
    private String contactLink;
    private boolean isAcceptingRequests; // matchStatus
}
