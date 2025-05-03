package com.team05.linkup.domain.user.application;

import com.team05.linkup.domain.community.infra.CommunityRepository;
import com.team05.linkup.domain.user.domain.User;
import com.team05.linkup.domain.user.dto.*;
import com.team05.linkup.domain.user.infrastructure.AreaRepository;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final Logger logger = LogManager.getLogger();
    private final AreaRepository areaRepository;

    public ProfilePageDTO getProfile(User user) {

        String areaName = areaRepository.findById(user.getAreaId())
                .map(area -> area.getAreaName())
                .orElse(null);

        boolean isCurrentUser = isCurrentUser(user);

        return ProfilePageDTO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .role(user.getRole().name())
                .tag(user.getProfileTag())
                .interest(user.getInterest().getDisplayName())
                .area(areaName)
                .introduction(user.getIntroduction())
                .me(isCurrentUser) // 현재 사용자와 비교해 설정 (예: SecurityContext에서 가져오기)
                .build();
    }
    private static boolean isCurrentUser(User user) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentProviderId = null;
        if (principal instanceof String) {
            currentProviderId = (String) principal;
        }
        logger.debug("사용자 provider Id: {}, 프로필 provider ID: {}", currentProviderId, user.getProviderId());
        return currentProviderId != null && currentProviderId.equals(user.getProviderId());
    }


    private final CommunityRepository communityRepository;

    private String getUserIdByNickname(String nickname) {
        return communityRepository.findUserIdByNickname(nickname);
    }

    public List<MyPostResponseDTO> getMyPosts(String nickname, int limit) {
        List<Object[]> rawResults = communityRepository.findByCommunityPosts(nickname, limit);

        return rawResults.stream()
                .map(obj -> new MyPostResponseDTO(
                        (String) obj[0],                                      // id
                        ((Timestamp) obj[1]).toLocalDateTime(),              // updated_at
                        (String) obj[2],                                      // category
                        (String) obj[3],                                      // title
                        (String) obj[4],                                      // content
                        ((Number) obj[5]).intValue(),                         // view_count (Long → int)
                        ((Number) obj[6]).intValue(),                         // like_count (Long → int)
                        ((Number) obj[7]).intValue()                          // comment_count (Long → int)
                ))
                .collect(Collectors.toList());
    }

    // 📌 내가 작성한 댓글 조회 - DTO 매핑 및 안전성 향상
    public List<MyCommentResponseDTO> getMyComments(String nickname, int limit) {
        // userId 조회
//        String userId = communityRepository.findUserIdByNickname(nickname); // ※ 아래에서 쿼리도 추가로 만들어줘야 함

        // ✅ userId 조회 (닉네임 기반 → ID 추출)
        String userId = getUserIdByNickname(nickname); // ✅ 변경된 부분
        // ✅ 댓글 데이터 조회
        List<Object[]> rows = communityRepository.findByMyCommunityComments(userId, limit);
        // ✅ DTO로 매핑
        return rows.stream()
                .map(row -> {
                        // 🛡️ null-safe 및 타입 캐스팅
                        Timestamp updatedAt = (Timestamp) row[0];
                        String description = (String) row[1];
                        String commentContent = (String) row[2];

                        return new MyCommentResponseDTO(
                                updatedAt != null ? updatedAt.toLocalDateTime() : null, // Timestamp가 null일 경우 NPE 방지
                                description,
                                commentContent
//                        ((Timestamp) row[0]).toLocalDateTime(),  // updated_at
//                        (String) row[1],                          // description
//                        (String) row[2] )                           // comment_content
                        );
                })
                .collect(Collectors.toList());
    }

    // 📌 내가 북마크한 게시글 조회 - DTO 매핑 및 null-safe 처리
     public List<MyBookmarkResponseDTO> getMyBookmarks(String nickname, int limit) {
//        String userId = communityRepository.findUserIdByNickname(nickname);

        // ✅ 닉네임 기반으로 userId 조회
        String userId = getUserIdByNickname(nickname); // ✅ 변경된 부분

        // ✅ 북마크한 게시글 데이터 조회
        List<Object[]> rawResults = communityRepository.findByMyBookmarks(userId, limit);

        // ✅ DTO로 매핑
        return rawResults.stream()
                .map(obj -> {

                    // 🛡️ null-safe 캐스팅
                    Timestamp updatedAt = (Timestamp) obj[0];
                    String title = (String) obj[1];
                    String content = (String) obj[2];

                    return new MyBookmarkResponseDTO(
                            updatedAt != null ? updatedAt.toLocalDateTime() : null, // Timestamp가 null일 경우 NPE 방지
                            title,
                            content
//                            ((Timestamp) obj[0]).toLocalDateTime(),
//                            (String) obj[1],
//                            (String) obj[2]
                    );
                })
                .collect(Collectors.toList());
    }

    // 📌 내가 좋아요한 게시글 조회 - DTO 매핑 및 null-safe 처리
    public List<MyLikeResponseDTO> getMyLikePosts(String nickname, int limit) {
//        String userId = communityRepository.findUserIdByNickname(nickname);

        // ✅ 닉네임 기반으로 userId 조회
        String userId = getUserIdByNickname(nickname); // ✅ 변경된 부분

        // ✅ 좋아요한 게시글 데이터 조회
        List<Object[]> rawResults = communityRepository.findByMyLikePosts(userId, limit);

        // ✅ DTO로 매핑
        return rawResults.stream()
                .map(obj -> {

                    // 🛡️ null-safe 캐스팅
                    Timestamp updatedAt = (Timestamp) obj[0];
                    String title = (String) obj[1];
                    String content = (String) obj[2];

                    return new MyLikeResponseDTO(
                            updatedAt != null ? updatedAt.toLocalDateTime() : null,
                            title,
                            content
//                            ((Timestamp) obj[0]).toLocalDateTime(),
//                            (String) obj[1],
//                            (String) obj[2]
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     *  활동 내역 중 공통 항목
     *  (서비스 단으로 리팩터링
     *      && Map<String, Object> -> DTO 형태로 변경)
     */
    public ActivityResponseDTO getCommonActivityDTO(String nickname) {
        return ActivityResponseDTO.builder()
                .nickname(nickname) //  "내 활동 내역" vs "OOO님의 활동 내역" 구분용 (프론트)
                .posts(getMyPosts(nickname, 2))
                .comments(getMyComments(nickname, 2))
                .bookmarks(getMyBookmarks(nickname, 1))
                .likes(getMyLikePosts(nickname, 1))
                .build();
    }
}