package com.team05.linkup.domain.user.application;

import com.team05.linkup.common.dto.UserPrincipal;
import com.team05.linkup.domain.community.infrastructure.CommunityRepository;
import com.team05.linkup.domain.mentoring.application.OngoingMatchingService;
import com.team05.linkup.domain.mentoring.dto.ReceivedReviewDTO;
import com.team05.linkup.domain.review.infrastructure.ReviewRepository;
import com.team05.linkup.domain.user.domain.Area;
import com.team05.linkup.domain.user.domain.Sigungu;
import com.team05.linkup.domain.user.domain.User;
import com.team05.linkup.domain.user.dto.*;
import com.team05.linkup.domain.user.infrastructure.SigunguRepository;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final Logger logger = LogManager.getLogger();
    private final SigunguRepository sigunguRepository;

    @Transactional(readOnly = true)
    public ProfilePageDTO getProfile(User user, UserPrincipal userPrincipal) {
        // Area 객체에서 지역 이름을 가져옵니다
        String areaName = Optional.ofNullable(user.getArea())
                .map(Area::getAreaName)
                .orElse(null);

        // 사용자의 sigunguCode가 있으면 해당 시군구 정보를 가져옵니다
        String sigungu = Optional.ofNullable(user.getArea())
                .flatMap(area -> Optional.ofNullable(user.getSigunguCode())
                        .flatMap(code -> sigunguRepository.findByIdAreacodeAndIdSigungucode(area.getAreacode(), code))
                        .map(Sigungu::getSigunguname))
                .orElse(null);

        boolean isCurrentUser = isCurrentUser(user, userPrincipal);

        return ProfilePageDTO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .role(user.getRole().name())
                .tag(user.getProfileTag())
                .interest(user.getInterest().getDisplayName())
                .area(areaName)
                .sigungu(sigungu)
                .introduction(user.getIntroduction())
                .me(isCurrentUser) // 현재 사용자와 비교해 설정 (예: SecurityContext에서 가져오기)
                .build();
    }

    public static boolean isCurrentUser(User user, UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return false;
        }

        String principalProvider = userPrincipal.provider();
        String principalProviderId = userPrincipal.providerId();
        String userProvider = user.getProvider();
        String userProviderId = user.getProviderId();

        logger.debug("Principal 사용자 provider: {}, Principal 사용자 provider Id: {}", principalProvider, principalProviderId);
        logger.debug("User 프로필 provider: {}, User 프로필 provider ID: {}", userProvider, userProviderId);

        return Objects.equals(principalProvider, userProvider) && Objects.equals(principalProviderId, userProviderId);
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

    // 🔧 내가 작성한 커뮤니티 게시글 - 페이징
    public Page<MyPostResponseDTO> getMyPostsPaged(String nickname, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Object[]> resultPage = communityRepository.findCommunityPostsWithPaging(nickname, pageable);

        return resultPage.map(obj -> new MyPostResponseDTO(
                (String) obj[0],                                      // id
                ((Timestamp) obj[1]).toLocalDateTime(),              // updated_at
                (String) obj[2],                                      // category
                (String) obj[3],                                      // title
                (String) obj[4],                                      // content
                ((Number) obj[5]).intValue(),                         // view_count
                ((Number) obj[6]).intValue(),                         // like_count
                ((Number) obj[7]).intValue()                          // comment_count
        ));
    }


    // 내가 작성한 댓글 조회
    public List<MyCommentResponseDTO> getMyComments(String nickname, int limit) {
        // userId 조회 (닉네임 기반 → ID 추출)
        String userId = getUserIdByNickname(nickname);
        // 댓글 데이터 조회
        List<Object[]> rows = communityRepository.findByMyCommunityComments(userId, limit);
        // DTO로 매핑
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
                        );
                })
                .collect(Collectors.toList());
    }

    public Page<MyCommentResponseDTO> getMyCommentsPaged(String nickname, int page, int size) {
        // 1. 닉네임으로 사용자 ID 조회
        String userId = getUserIdByNickname(nickname);
        // 2. 페이징 객체 생성
        Pageable pageable = PageRequest.of(page, size);
        // 3. native 쿼리 결과 받아오기
        Page<Object[]> resultPage = communityRepository.findMyCommentsPaged(userId, pageable);

        // 4. Object[] → DTO 매핑
        return resultPage.map(row -> {
            Timestamp updatedAt = (Timestamp) row[0];
            String description = (String) row[1];
            String commentContent = (String) row[2];

            return new MyCommentResponseDTO(
                    updatedAt != null ? updatedAt.toLocalDateTime() : null,
                    description,
                    commentContent
            );
        });
    }


    // 내가 북마크한 게시글 조회
     public List<MyBookmarkResponseDTO> getMyBookmarks(String nickname, int limit) {
        // 닉네임 기반으로 userId 조회
        String userId = getUserIdByNickname(nickname);
        // 북마크한 게시글 데이터 조회
        List<Object[]> rawResults = communityRepository.findByMyBookmarks(userId, limit);

        // DTO로 매핑
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
                    );
                })
                .collect(Collectors.toList());
    }

    // 내가 좋아요한 게시글 조회
    public List<MyLikeResponseDTO> getMyLikePosts(String nickname, int limit) {
        // 닉네임 기반으로 userId 조회
        String userId = getUserIdByNickname(nickname);
        // 좋아요한 게시글 데이터 조회
        List<Object[]> rawResults = communityRepository.findByMyLikePosts(userId, limit);

        // DTO로 매핑
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
                    );
                })
                .collect(Collectors.toList());
    }

    // 활동 내역 중 공통 항목
    public ActivityResponseDTO getCommonActivityDTO(String nickname) {
        return ActivityResponseDTO.builder()
                .nickname(nickname) //  "내 활동 내역" vs "OOO님의 활동 내역" 구분용 (프론트)
                .posts(getMyPosts(nickname, 2))
                .comments(getMyComments(nickname, 2))
                .bookmarks(getMyBookmarks(nickname, 1))
                .likes(getMyLikePosts(nickname, 1))
                .build();
    }

    private final MentorProfileService mentorProfileService;
    private final OngoingMatchingService ongoingMatchingService;

    public MyMatchingPageDTO getMatchingPageData(User mentor) {
        return MyMatchingPageDTO.builder()
                .reviews(getReviewsForMentor(mentor.getId(), 2))
                .communityQnAs(getRecentQnAByInterest(mentor.getInterest().name(), 2))
                .ongoingMatchings(
                        ongoingMatchingService.getOngoingMatchingsForMentor(mentor.getId(), 2))
                .stats(mentorProfileService.getMentoringStats(UUID.fromString(mentor.getId())))
                .build();
    }

    private final ReviewRepository reviewRepository;

    // 받은 리뷰 조회 메서드 (멘토만 대상)
    public List<ReceivedReviewDTO> getReviewsForMentor(String mentorId, int limit) {
        // 쿼리 결과 받아오기
        List<Object[]> rawResults = reviewRepository.findReceivedReviewsByMentorId(mentorId, limit);

        // DTO로 매핑
        return rawResults.stream()
                .map(obj -> ReceivedReviewDTO.builder()
                        .reviewerName((String) obj[0])  // 리뷰 작성자 이름
                        .reviewerProfileImageUrl((String) obj[1])   //  리뷰 작성자 프로필 사진
                        .reviewDate(((Timestamp) obj[2]).toLocalDateTime().toLocalDate().toString())
                        .star(BigDecimal.valueOf(((Number) obj[3]).doubleValue()))  // 별점
                        .content((String) obj[4])   // 리뷰 내용
                        .build())
                .collect(Collectors.toList());
    }

    // 최근 QnA 조회 (QueryDSL 결과 → 후처리)
    public List<CommunityQnAPostResponseDTO> getRecentQnAByInterest(String interest, int limit) {
        List<CommunityQnAPostDTO> rawResults = communityRepository.findRecentQnAPostsByInterest(interest, limit);

        return rawResults.stream()
                .map(dto -> CommunityQnAPostResponseDTO.builder()
                        .postId(dto.getPostId())
                        .nickname(dto.getNickname())
                        .profileImageUrl(dto.getProfileImageUrl())
                        .createdAt(dto.getCreatedAt())
                        .title(dto.getTitle())
                        .content(dto.getContent())
                        .tags(parseTags(dto.getTagName()))   // 후처리
                        .commentCount(dto.getCommentCount())
                        .build())
                .collect(Collectors.toList());
    }

    // 태그 문자열 → List<String> 변환
    private List<String> parseTags(String tagString) {
        if (tagString == null || tagString.isBlank()) return List.of();
        return Arrays.stream(tagString.split(","))
                .map(String::trim)
                .toList();
    }

}
