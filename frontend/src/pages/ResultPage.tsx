import { useEffect, useMemo, useRef, useState } from "react";
import axios from "axios";
import { useLocation, useNavigate } from "react-router-dom";
import { recommendByKeyword } from "../api/recommendation";
import Button from "../components/Button";
import Header from "../components/Header";
import {
    buildRecommendationCacheKey,
    clearRecommendationCache,
    loadRecommendationCache,
    saveRecommendationCache,
    type RecommendationCacheEntry,
} from "../utils/recommendationCache";
import type {
    KeywordRecommendResult,
    KeywordRecommendation,
    KeywordRecommendationCategory,
    RecommendationCategoryKey,
    ResolvedKakaoPlace,
} from "../types/recommendation";

const CATEGORY_STYLE: Record<string, string> = {
    RESTAURANT: "border-rose-200 bg-rose-50/80",
    CAFE: "border-amber-200 bg-amber-50/80",
    BAR: "border-violet-200 bg-violet-50/80",
    PC_ROOM: "border-indigo-200 bg-indigo-50/80",
    KARAOKE: "border-fuchsia-200 bg-fuchsia-50/80",
    SHOPPING: "border-emerald-200 bg-emerald-50/80",
    ACTIVITY: "border-sky-200 bg-sky-50/80",
};

const CATEGORY_BADGE: Record<string, string> = {
    RESTAURANT: "bg-rose-100 text-rose-700",
    CAFE: "bg-amber-100 text-amber-700",
    BAR: "bg-violet-100 text-violet-700",
    PC_ROOM: "bg-indigo-100 text-indigo-700",
    KARAOKE: "bg-fuchsia-100 text-fuchsia-700",
    SHOPPING: "bg-emerald-100 text-emerald-700",
    ACTIVITY: "bg-sky-100 text-sky-700",
};

const CATEGORY_PIN_COLOR: Record<string, string> = {
    RESTAURANT: "#f43f5e",
    CAFE: "#f59e0b",
    BAR: "#8b5cf6",
    PC_ROOM: "#4f46e5",
    KARAOKE: "#d946ef",
    SHOPPING: "#10b981",
    ACTIVITY: "#0ea5e9",
};

const CATEGORY_PLACE_TARGET = 3;
const MATCH_THRESHOLD = 10;
const MAX_KAKAO_QUERY_LENGTH = 45;
const MIN_DISTANCE_KM = 1.5;
const MAX_DISTANCE_KM = 8;
const DEFAULT_DISTANCE_KM = 2.8;
const ITEM_QUERY_LIMIT = 2;
const REPLACEMENT_QUERY_LIMIT = 3;
const DEFAULT_SEARCH_PAGE_SIZE = 15;
const DEFAULT_SEARCH_MAX_PAGES = 1;

const formatRecommendationSource = (source: string) => {
    if (source === "AI") return "AI";
    if (source === "DB") return "내부 후보 데이터";
    if (source === "FALLBACK") return "기본 추천";
    return source || "대기 중";
};

const formatCategoryTitle = (category: RecommendationCategoryKey | undefined) => {
    if (category === "RESTAURANT") return "식당";
    if (category === "CAFE") return "카페";
    if (category === "BAR") return "술집";
    if (category === "PC_ROOM") return "PC방";
    if (category === "KARAOKE") return "코인노래방";
    if (category === "SHOPPING") return "쇼핑";
    if (category === "ACTIVITY") return "놀거리";
    return "추천";
};

const normalizeText = (value: string) =>
    value
        .toLowerCase()
        .replaceAll("&", "and")
        .replace(/[^\p{L}\p{N}]/gu, "");

const tokenizeText = (value: string) =>
    value
        .split(/[\s,/()\-_.]+/)
        .map((token) => token.trim())
        .filter((token) => token.length >= 2);

const escapeHtml = (value: string) =>
    value
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");

const sortCategories = (categories: KeywordRecommendationCategory[]) =>
    [...categories];

const normalizeKeywordRecommendResult = (value: unknown): KeywordRecommendResult => {
    const candidate = (value && typeof value === "object")
        ? (value as Partial<KeywordRecommendResult>)
        : {};

    return {
        recommendations: Array.isArray(candidate.recommendations) ? candidate.recommendations : [],
        categories: Array.isArray(candidate.categories) ? candidate.categories : [],
        routes: Array.isArray(candidate.routes) ? candidate.routes : [],
        source: typeof candidate.source === "string" ? candidate.source : "",
        warning: typeof candidate.warning === "string" || candidate.warning === null
            ? candidate.warning
            : undefined,
    };
};

const buildInitialSelections = (categories: KeywordRecommendationCategory[]) => {
    const entries = categories
        .map((category) => [category.key, category.places[0]] as const)
        .filter((entry): entry is readonly [string, KeywordRecommendation] => Boolean(entry[1]));

    return Object.fromEntries(entries);
};

const buildPlaceKey = (item: Pick<KeywordRecommendation, "category" | "name" | "address">) =>
    [
        item.category || "ACTIVITY",
        normalizeText(item.name),
        normalizeText(item.address),
    ].join("::");

const uniqueQueries = (queries: Array<string | undefined>) =>
    queries
        .map((query) => sanitizeKakaoQuery((query || "").trim()))
        .filter((query, index, array): query is string => Boolean(query) && array.indexOf(query) === index);

const buildMapQuery = (placeName: string, address: string, areaHint: string) => {
    const addressHint = address.split(" ").slice(0, 3).join(" ").trim();
    return [placeName, areaHint || addressHint].filter(Boolean).join(" ").trim();
};

const sanitizeKakaoQuery = (query: string) =>
    query
        // Remove punctuation/noise that commonly causes low-quality keyword searches.
        .replace(/[^\w\u3131-\u318E\uAC00-\uD7A3\s]/g, " ")
        .replace(/\s+/g, " ")
        .trim()
        .slice(0, MAX_KAKAO_QUERY_LENGTH);

const clampDistanceKm = (value: number) =>
    Math.max(MIN_DISTANCE_KM, Math.min(MAX_DISTANCE_KM, value));

const resolveAdaptiveDistanceKm = (areaHint: string, planHint: string) => {
    const normalizedArea = areaHint.replace(/\s+/g, "");
    const normalizedPlan = normalizeHint(planHint);

    let base = DEFAULT_DISTANCE_KM;
    if (!normalizedArea) {
        base = 3.4;
    } else if (normalizedArea.endsWith("역") || normalizedArea.endsWith("동")) {
        base = 2.0;
    } else if (normalizedArea.endsWith("구")) {
        base = 2.8;
    } else if (normalizedArea.endsWith("시") || normalizedArea.endsWith("군")) {
        base = 4.0;
    }

    if (containsAny(normalizedPlan, ["도보", "걷기", "가깝", "근처"])) {
        base -= 0.4;
    }
    if (containsAny(normalizedPlan, ["여행", "관광", "드라이브"])) {
        base += 0.4;
    }

    return clampDistanceKm(Math.round(base * 10) / 10);
};

const formatDistanceKm = (value: number) => (Number.isInteger(value) ? `${value}` : value.toFixed(1));

const AREA_SUFFIX_PATTERN = /^(?:[가-힣A-Za-z0-9]+)(역|동|구|시|군|읍|면|리)$/;
const AREA_TRAILING_MARKERS = ["에서", "근처", "근처에서", "주변", "주변에서", "인근", "인근에서", "일대", "일대에서", "쪽", "쪽에서", "부근", "부근에서"];
const AREA_INLINE_PATTERN = /([가-힣A-Za-z0-9]{2,12})\s*(?:근처에서|근처|주변에서|주변|인근에서|인근|일대에서|일대|부근에서|부근|쪽에서|쪽|에서)/g;
const AREA_PREFIX_TRAVEL_PATTERN = /([가-힣A-Za-z0-9]{2,12})\s*(?:여행|데이트|나들이|모임)/g;
const GENERIC_AREA_HINTS = new Set([
    "근처",
    "주변",
    "인근",
    "일대",
    "부근",
    "여기",
    "저기",
    "그근처",
    "근방",
    "주위",
    "지역",
]);
const AREA_STOPWORDS = new Set([
    "친구",
    "연인",
    "가족",
    "모임",
    "여행",
    "관광",
    "동선",
    "메뉴",
    "맛집",
    "카페",
    "식당",
    "술집",
    "코스",
    "근처",
    "주변",
    "인근",
    "일대",
    "부근",
]);

const extractAreaFromText = (text: string) => {
    const isGenericAreaWord = (value: string) =>
        GENERIC_AREA_HINTS.has(value.replace(/\s+/g, "").toLowerCase());
    const normalized = text
        .replace(/[^\w\u3131-\u318E\uAC00-\uD7A3\s]/g, " ")
        .replace(/\s+/g, " ")
        .trim();
    if (!normalized) return "";

    const inlineMatches = normalized.matchAll(AREA_INLINE_PATTERN);
    for (const match of inlineMatches) {
        const candidate = (match[1] || "").trim();
        if (candidate.length >= 2 && !isGenericAreaWord(candidate)) {
            return candidate.slice(0, 20);
        }
    }

    const travelMatches = normalized.matchAll(AREA_PREFIX_TRAVEL_PATTERN);
    for (const match of travelMatches) {
        const candidate = (match[1] || "").trim();
        if (candidate.length >= 2 && !isGenericAreaWord(candidate)) {
            return candidate.slice(0, 20);
        }
    }

    const tokens = normalized.split(" ").filter(Boolean);

    for (let index = 0; index < tokens.length; index += 1) {
        const token = tokens[index];

        for (const marker of AREA_TRAILING_MARKERS) {
            if (token.endsWith(marker) && token.length > marker.length) {
                const candidate = token.slice(0, -marker.length).trim();
                if (candidate.length >= 2 && !isGenericAreaWord(candidate)) {
                    return candidate.slice(0, 20);
                }
            }
        }

        if (AREA_TRAILING_MARKERS.includes(token) && index > 0) {
            const previous = tokens[index - 1].trim();
            if (previous.length >= 2 && !isGenericAreaWord(previous)) {
                return previous.slice(0, 20);
            }
        }
    }

    const bySuffix = tokens.find((token) => AREA_SUFFIX_PATTERN.test(token) && !isGenericAreaWord(token));
    if (bySuffix) {
        return bySuffix.slice(0, 20);
    }

    const firstLocationLikeToken = tokens.find((token) => {
        if (token.length < 2) return false;
        if (isGenericAreaWord(token)) return false;
        if (AREA_STOPWORDS.has(token)) return false;
        if (/[0-9]/.test(token)) return false;
        return true;
    });
    if (firstLocationLikeToken) {
        return firstLocationLikeToken.slice(0, 20);
    }

    return "";
};

const resolveAreaHint = (preferredLocation: string, keyword: string, planHint: string) => {
    const trimGenericSuffix = (value: string) => {
        let next = value.trim();
        const genericSuffixes = ["근처", "주변", "인근", "일대", "부근", "쪽"];
        let changed = true;
        while (changed) {
            changed = false;
            for (const suffix of genericSuffixes) {
                if (next.endsWith(suffix) && next.length > suffix.length) {
                    next = next.slice(0, -suffix.length).trim();
                    changed = true;
                }
            }
        }
        return next;
    };

    const normalizeAreaCandidate = (value: string) => {
        const sanitized = sanitizeKakaoQuery(trimGenericSuffix(value));
        if (!sanitized) {
            return "";
        }
        const normalized = sanitized.replace(/\s+/g, "").toLowerCase();
        return GENERIC_AREA_HINTS.has(normalized) ? "" : sanitized;
    };

    const explicitFromText = normalizeAreaCandidate(extractAreaFromText(preferredLocation));
    if (explicitFromText) return explicitFromText;

    const explicit = normalizeAreaCandidate(preferredLocation);
    if (explicit) return explicit;

    const fromKeyword = normalizeAreaCandidate(extractAreaFromText(keyword));
    if (fromKeyword) return fromKeyword;

    const directKeyword = normalizeAreaCandidate(keyword);
    if (directKeyword && directKeyword.split(" ").length <= 2) {
        return directKeyword;
    }

    const fromPlanHint = normalizeAreaCandidate(extractAreaFromText(planHint));
    if (fromPlanHint) return fromPlanHint;

    return "";
};

const hasNameTokenOverlap = (sourceName: string, targetName: string) => {
    const tokens = tokenizeText(sourceName);
    return tokens.some((token) => targetName.includes(token));
};

const normalizeHint = (value: string) => value.toLowerCase().replace(/\s+/g, "");

const containsAny = (value: string, needles: string[]) =>
    needles.some((needle) => value.includes(needle));

const extractAvoidHint = (planHint: string) => {
    const match = planHint.match(/피하고\s*싶은\s*것:\s*([^\n]+)/i);
    return normalizeHint(match?.[1] || "");
};

const isWesternRestaurantIntent = (normalizedPlanHint: string) =>
    containsAny(normalizedPlanHint, ["양식", "파스타", "스테이크", "이탈리안", "레스토랑", "비스트로", "브런치"]);

const isJapaneseRestaurantIntent = (normalizedPlanHint: string) =>
    containsAny(normalizedPlanHint, ["일식", "스시", "초밥", "오마카세", "라멘", "우동", "돈카츠"]);

const isChineseRestaurantIntent = (normalizedPlanHint: string) =>
    containsAny(normalizedPlanHint, ["중식", "짜장", "짬뽕", "마라", "딤섬", "중국집"]);

const isKoreanRestaurantIntent = (normalizedPlanHint: string) =>
    containsAny(normalizedPlanHint, ["한식", "국밥", "삼겹살", "갈비", "곱창", "족발", "보쌈", "고깃집"]);

const wantsDessertCafe = (normalizedPlanHint: string) =>
    containsAny(normalizedPlanHint, ["케이크", "디저트", "베이커리", "타르트", "구움과자", "빙수"]);

const wantsAtmosphericCafe = (normalizedPlanHint: string) =>
    containsAny(normalizedPlanHint, ["분위기좋", "느낌좋", "감성", "예쁜", "사진"]);

const wantsSamgyeopsal = (normalizedPlanHint: string) =>
    containsAny(normalizedPlanHint, ["삼겹살", "숙성삼겹", "돼지고기", "오겹살"]);

const wantsQuietBar = (normalizedPlanHint: string) =>
    containsAny(normalizedPlanHint, ["조용한술집", "조용한바", "조용히술", "시끄러운술집제외", "시끄러운술집말고"]);

const wantsKaraoke = (normalizedPlanHint: string) =>
    containsAny(normalizedPlanHint, ["코노", "코인노래방", "노래방"]);

const wantsWalkingActivity = (normalizedPlanHint: string) =>
    containsAny(normalizedPlanHint, ["산책", "걷", "호수", "공원"]);

const wantsBrowseActivity = (normalizedPlanHint: string) =>
    containsAny(normalizedPlanHint, ["구경", "쇼핑", "소품샵", "편집샵", "서점", "백화점"]);

const buildRestaurantFocusTags = (normalizedPlanHint: string) => {
    if (wantsSamgyeopsal(normalizedPlanHint)) return ["#삼겹살", "#고깃집", "#저녁식사"];
    if (isWesternRestaurantIntent(normalizedPlanHint)) return ["#양식", "#파스타or스테이크", "#저녁식사"];
    if (isJapaneseRestaurantIntent(normalizedPlanHint)) return ["#일식", "#일식저녁", "#코스식사"];
    if (isChineseRestaurantIntent(normalizedPlanHint)) return ["#중식", "#중식저녁", "#식사코스"];
    if (isKoreanRestaurantIntent(normalizedPlanHint)) return ["#한식", "#든든한식사", "#저녁식사"];
    return ["#식사후보", "#접근성무난", "#모임식사"];
};

const buildCafeFocusTags = (normalizedPlanHint: string) => {
    if (wantsDessertCafe(normalizedPlanHint)) return ["#케이크", "#디저트카페", "#카페후보"];
    if (wantsAtmosphericCafe(normalizedPlanHint)) return ["#감성카페", "#분위기좋음", "#카페후보"];
    return ["#카페후보", "#대화하기좋음", "#쉬어가기좋음"];
};

const buildDefaultReason = (
    categoryKey: RecommendationCategoryKey,
    areaHint: string,
    planHint: string,
) => {
    const normalizedPlanHint = normalizeHint(planHint);
    if (categoryKey === "RESTAURANT" && wantsSamgyeopsal(normalizedPlanHint)) {
        return `${areaHint || "이 지역"}에서 삼겹살이나 구이류 저녁 코스로 이어가기 좋은 식당 후보입니다.`;
    }
    if (categoryKey === "RESTAURANT" && isWesternRestaurantIntent(normalizedPlanHint)) {
        return `${areaHint || "이 지역"}에서 양식 저녁 코스로 이어가기 좋은 식당 후보입니다.`;
    }
    if (categoryKey === "RESTAURANT" && isJapaneseRestaurantIntent(normalizedPlanHint)) {
        return `${areaHint || "이 지역"}에서 일식 저녁 코스로 이어가기 좋은 식당 후보입니다.`;
    }
    if (categoryKey === "RESTAURANT" && isChineseRestaurantIntent(normalizedPlanHint)) {
        return `${areaHint || "이 지역"}에서 중식 식사 코스로 이어가기 좋은 식당 후보입니다.`;
    }
    if (categoryKey === "RESTAURANT" && isKoreanRestaurantIntent(normalizedPlanHint)) {
        return `${areaHint || "이 지역"}에서 한식 식사 흐름으로 이어가기 좋은 식당 후보입니다.`;
    }
    if (categoryKey === "RESTAURANT") return `${areaHint || "이 지역"}에서 식사 시작점으로 무난한 후보입니다.`;
    if (categoryKey === "CAFE" && wantsDessertCafe(normalizedPlanHint)) {
        return `${areaHint || "이 지역"}에서 케이크나 디저트를 즐기며 쉬어가기 좋은 카페 후보입니다.`;
    }
    if (categoryKey === "CAFE" && wantsAtmosphericCafe(normalizedPlanHint)) {
        return `${areaHint || "이 지역"}에서 분위기를 살리며 머물기 좋은 카페 후보입니다.`;
    }
    if (categoryKey === "CAFE") return `${areaHint || "이 지역"}에서 쉬어가기 좋은 카페 후보입니다.`;
    if (categoryKey === "BAR" && wantsQuietBar(normalizedPlanHint)) {
        return `${areaHint || "이 지역"}에서 조용히 이야기 나누며 2차로 이어가기 좋은 술집 후보입니다.`;
    }
    if (categoryKey === "BAR") return `${areaHint || "이 지역"}에서 2차로 이어가기 좋은 술집 후보입니다.`;
    if (categoryKey === "PC_ROOM") return `${areaHint || "이 지역"}에서 바로 같이 놀기 좋은 PC방 후보입니다.`;
    if (categoryKey === "KARAOKE") return `${areaHint || "이 지역"}에서 마무리 동선으로 넣기 좋은 코인노래방 후보입니다.`;
    if (categoryKey === "SHOPPING") return `${areaHint || "이 지역"}에서 함께 둘러보며 시간을 보내기 좋은 쇼핑 후보입니다.`;
    if (categoryKey === "ACTIVITY" && wantsWalkingActivity(normalizedPlanHint)) {
        return `${areaHint || "이 지역"}에서 식사 전후로 산책하기 좋은 후보입니다.`;
    }
    if (categoryKey === "ACTIVITY" && wantsBrowseActivity(normalizedPlanHint)) {
        return `${areaHint || "이 지역"}에서 함께 구경하며 시간을 보내기 좋은 후보입니다.`;
    }
    return `${areaHint || "이 지역"}에서 함께 즐기기 좋은 놀거리 후보입니다.`;
};

const buildDefaultTags = (
    categoryKey: RecommendationCategoryKey,
    planHint: string,
): string[] => {
    const normalizedPlanHint = normalizeHint(planHint);
    if (categoryKey === "RESTAURANT") return buildRestaurantFocusTags(normalizedPlanHint);
    if (categoryKey === "CAFE") return buildCafeFocusTags(normalizedPlanHint);
    if (categoryKey === "BAR") {
        if (wantsQuietBar(normalizedPlanHint)) return ["#조용한술집", "#대화하기좋음", "#2차추천"];
        return ["#술집후보", "#2차추천", "#한잔하기좋음"];
    }
    if (categoryKey === "PC_ROOM") return ["#PC방후보", "#같이놀기좋음", "#실내활동"];
    if (categoryKey === "KARAOKE") return ["#코인노래방", "#마무리코스", "#친구모임"];
    if (categoryKey === "SHOPPING") return ["#쇼핑하기좋음", "#구경거리", "#함께보기좋음"];
    if (wantsWalkingActivity(normalizedPlanHint)) return ["#산책", "#걷기좋음", "#데이트동선"];
    if (wantsBrowseActivity(normalizedPlanHint)) return ["#구경거리", "#함께보기좋음", "#동선포인트"];
    return ["#놀거리후보", "#함께가기좋음", "#분위기전환"];
};

const matchesCategoryIntent = (
    categoryKey: RecommendationCategoryKey,
    result: KakaoKeywordResult,
    planHint: string,
    normalizedPlanHint: string,
) => {
    const haystack = `${result.place_name || ""} ${result.category_name || ""}`.toLowerCase();
    const avoidHint = extractAvoidHint(planHint);

    if (avoidHint && containsAny(avoidHint, ["치킨"]) && containsAny(haystack, ["치킨", "닭"])) {
        return false;
    }
    if (avoidHint && containsAny(avoidHint, ["프랜차이즈", "프랜차이즈말고"])) {
        if (containsAny(haystack, ["bhc", "bbq", "교촌", "스타벅스", "투썸", "메가커피", "빽다방", "컴포즈"])) {
            return false;
        }
    }

    if (categoryKey === "RESTAURANT") {
        if (wantsSamgyeopsal(normalizedPlanHint)) {
            return containsAny(haystack, ["삼겹살", "돼지", "돼지고기", "오겹살", "고깃집", "구이", "숯불", "갈비"])
                && !containsAny(haystack, ["설렁탕", "국밥", "곰탕", "감자탕", "물산", "수산", "횟집"]);
        }
        if (isWesternRestaurantIntent(normalizedPlanHint)) {
            return containsAny(haystack, ["양식", "파스타", "스테이크", "이탈리안", "비스트로", "레스토랑", "브런치"])
                && !containsAny(haystack, ["치킨", "수산", "횟집", "해산물", "국밥", "감자탕", "족발", "정육", "물산"]);
        }
        if (isJapaneseRestaurantIntent(normalizedPlanHint)) {
            return containsAny(haystack, ["일식", "스시", "초밥", "오마카세", "라멘", "우동", "돈카츠"]);
        }
        if (isChineseRestaurantIntent(normalizedPlanHint)) {
            return containsAny(haystack, ["중식", "중국", "짜장", "짬뽕", "마라", "딤섬"]);
        }
        if (isKoreanRestaurantIntent(normalizedPlanHint)) {
            return containsAny(haystack, ["한식", "국밥", "삼겹살", "갈비", "곱창", "족발", "보쌈", "고기"]);
        }
        return containsAny(haystack, ["음식점", "식당", "맛집", "고기", "파스타", "피자", "밥", "라멘", "국밥", "초밥", "치킨"]);
    }
    if (categoryKey === "CAFE") {
        if (wantsDessertCafe(normalizedPlanHint)) {
            return containsAny(haystack, ["카페", "디저트", "케이크", "베이커리", "타르트", "구움과자"]);
        }
        return containsAny(haystack, ["카페", "커피", "디저트", "베이커리"]);
    }
    if (categoryKey === "BAR") {
        if (wantsQuietBar(normalizedPlanHint)) {
            return containsAny(haystack, ["술집", "주점", "와인", "하이볼", "bar", "바", "라운지", "이자카야", "펍", "칵테일"])
                && !containsAny(haystack, ["클럽", "나이트", "헌팅포차", "감성주점", "노래타운"]);
        }
        return containsAny(haystack, ["술집", "주점", "포차", "호프", "이자카야", "와인", "맥주", "하이볼", "bar", "펍"])
            && !containsAny(haystack, ["노래타운"]);
    }
    if (categoryKey === "PC_ROOM") {
        return containsAny(haystack, ["pc", "피시", "인터넷", "게임"]);
    }
    if (categoryKey === "KARAOKE") {
        return containsAny(haystack, ["노래방", "코인노래방", "코노"]);
    }
    if (categoryKey === "SHOPPING") {
        return containsAny(haystack, ["쇼핑", "백화점", "아울렛", "편집샵", "소품", "서점", "쇼룸", "상점", "스토어", "몰"]);
    }
    if (wantsKaraoke(normalizedPlanHint)) {
        return containsAny(haystack, ["노래방", "코인노래방", "코노"]);
    }
    if (containsAny(normalizedPlanHint, ["보드게임"])) {
        return containsAny(haystack, ["보드게임"]);
    }
    if (containsAny(normalizedPlanHint, ["방탈출"])) {
        return containsAny(haystack, ["방탈출"]);
    }
    if (wantsWalkingActivity(normalizedPlanHint)) {
        return containsAny(haystack, ["공원", "산책", "거리", "호수", "둘레길"]);
    }
    if (wantsBrowseActivity(normalizedPlanHint)) {
        return containsAny(haystack, ["소품", "편집샵", "서점", "백화점", "쇼핑", "거리", "몰"]);
    }
    return containsAny(haystack, ["노래방", "보드게임", "방탈출", "오락실", "vr", "볼링", "당구", "공방", "전시"]);
};

const buildCategoryQueries = (
    categoryKey: RecommendationCategoryKey,
    areaHint: string,
    planHint: string,
) => {
    const normalizedPlanHint = normalizeHint(planHint);
    const seeds: string[] = [];
    const avoidHint = extractAvoidHint(planHint);

    if (categoryKey === "RESTAURANT") {
        if (wantsSamgyeopsal(normalizedPlanHint)) {
            seeds.push("삼겹살", "고깃집", "돼지고기 구이", "숙성 삼겹살");
        }
        if (isWesternRestaurantIntent(normalizedPlanHint)) {
            seeds.push("양식", "파스타", "스테이크", "이탈리안 레스토랑", "비스트로");
        } else if (isJapaneseRestaurantIntent(normalizedPlanHint)) {
            seeds.push("일식", "오마카세", "초밥", "라멘");
        } else if (isChineseRestaurantIntent(normalizedPlanHint)) {
            seeds.push("중식", "짬뽕", "짜장", "딤섬");
        } else if (isKoreanRestaurantIntent(normalizedPlanHint)) {
            seeds.push("한식", "국밥", "삼겹살", "갈비", "고깃집");
        }
        if (containsAny(normalizedPlanHint, ["피자"])) seeds.push("피자");
        if (containsAny(normalizedPlanHint, ["파스타"])) seeds.push("파스타");
        if (containsAny(normalizedPlanHint, ["스테이크"])) seeds.push("스테이크");
        if (containsAny(normalizedPlanHint, ["고기", "삼겹살"])) seeds.push("고기집");
        if (seeds.length === 0 && !(avoidHint && containsAny(avoidHint, ["치킨"]))) {
            seeds.push("맛집", "밥집", "식당");
        }
        if (seeds.length === 0) {
            seeds.push("식당");
        }
    } else if (categoryKey === "CAFE") {
        if (wantsDessertCafe(normalizedPlanHint)) {
            seeds.push("케이크 카페", "디저트 카페", "베이커리 카페");
        }
        if (wantsAtmosphericCafe(normalizedPlanHint)) {
            seeds.push("분위기 좋은 카페", "감성 카페");
        }
        seeds.push("카페", "커피");
    } else if (categoryKey === "BAR") {
        if (wantsQuietBar(normalizedPlanHint)) {
            seeds.push("조용한 술집", "하이볼 바", "와인바", "조용한 이자카야");
        }
        seeds.push("술집", "이자카야", "하이볼");
    } else if (categoryKey === "PC_ROOM") {
        seeds.push("PC방", "피시방", "게임장");
    } else if (categoryKey === "KARAOKE") {
        seeds.push("코인노래방", "노래방", "코노");
    } else if (categoryKey === "SHOPPING") {
        seeds.push("편집샵", "쇼핑몰", "백화점", "소품샵", "서점");
    } else {
        if (wantsWalkingActivity(normalizedPlanHint)) seeds.push("산책", "공원", "산책로", "호수");
        if (wantsBrowseActivity(normalizedPlanHint)) seeds.push("소품샵", "편집샵", "서점", "쇼핑");
        if (containsAny(normalizedPlanHint, ["보드게임"])) seeds.push("보드게임카페");
        if (containsAny(normalizedPlanHint, ["방탈출"])) seeds.push("방탈출");
        seeds.push("놀거리", "오락실");
    }

    const scopedQueries = seeds.map((seed) => `${areaHint} ${seed}`.trim());
    if (areaHint.trim()) {
        return uniqueQueries([
            ...scopedQueries,
            ...seeds.map((seed) => seed.trim()),
        ]);
    }
    return uniqueQueries(scopedQueries);
};

type RouteState = {
    meetingType?: string;
    mood?: string;
    location?: string;
    planHint?: string;
};

type KakaoKeywordResult = {
    place_name: string;
    x: string;
    y: string;
    place_url?: string;
    road_address_name?: string;
    address_name?: string;
    phone?: string;
    category_name?: string;
    category_group_code?: string;
};

type ScoredKeywordResult = {
    result: KakaoKeywordResult;
    score: number;
    query: string;
};

type VerificationBundle = {
    categories: KeywordRecommendationCategory[];
    resolvedPlaces: Record<string, ResolvedKakaoPlace>;
    droppedCount: number;
    replacedCount: number;
};

type AreaCenter = {
    x: number;
    y: number;
    radius: number;
    maxDistance: number;
};

const toRadians = (value: number) => (value * Math.PI) / 180;

const distanceInMeters = (
    lat1: number,
    lng1: number,
    lat2: number,
    lng2: number,
) => {
    const earthRadius = 6371000;
    const dLat = toRadians(lat2 - lat1);
    const dLng = toRadians(lng2 - lng1);
    const a = Math.sin(dLat / 2) ** 2
        + Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) * Math.sin(dLng / 2) ** 2;
    return 2 * earthRadius * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
};

const getAreaRadius = (areaHint: string, maxDistanceMeters: number) => {
    const normalized = areaHint.replace(/\s+/g, "");
    const fallbackRadius = !normalized
        ? 3500
        : normalized.includes("서울") || normalized.includes("부산") || normalized.includes("대구")
            ? 5000
            : normalized.includes("구") || normalized.includes("시")
                ? 3500
                : normalized.includes("역") || normalized.includes("동")
                    ? 2200
                    : 2800;
    const normalizedLimit = Number.isFinite(maxDistanceMeters) ? maxDistanceMeters : fallbackRadius;
    return Math.max(1000, Math.min(12000, Math.round(normalizedLimit)));
};

const getMaxAllowedDistance = (areaCenter: AreaCenter) => areaCenter.maxDistance;

const getCategoryPinColor = (categoryKey: RecommendationCategoryKey | undefined) =>
    CATEGORY_PIN_COLOR[categoryKey || ""] || "#2563eb";

const scoreAreaCenterCandidate = (
    areaHint: string,
    result: KakaoKeywordResult,
) => {
    const normalizedHint = areaHint.replace(/\s+/g, "").toLowerCase();
    const normalizedName = (result.place_name || "").replace(/\s+/g, "").toLowerCase();
    const normalizedAddress = `${result.address_name || ""} ${result.road_address_name || ""}`
        .replace(/\s+/g, "")
        .toLowerCase();
    const categoryName = (result.category_name || "").toLowerCase();
    const categoryGroupCode = (result.category_group_code || "").toUpperCase();

    let score = 0;
    if (!normalizedHint) {
        return score;
    }

    if (normalizedName === normalizedHint) {
        score += 30;
    }
    if (normalizedName === `${normalizedHint}역` || normalizedName.endsWith(`${normalizedHint}역`)) {
        score += 40;
    }
    if (normalizedName.startsWith(normalizedHint)) {
        score += 16;
    }
    if (normalizedName.includes(normalizedHint)) {
        score += 10;
    }
    if (normalizedAddress.includes(normalizedHint)) {
        score += 14;
    }
    if (categoryGroupCode === "SW8") {
        score += 24;
    }
    if (containsAny(categoryName, ["지하철역", "역사", "행정동", "법정동", "주민센터", "사거리", "교차로"])) {
        score += 12;
    }

    if (containsAny(`${normalizedName} ${categoryName}`, [
        "카페",
        "식당",
        "맛집",
        "피시",
        "pc",
        "노래방",
        "술집",
        "와인",
        "바",
        "마트",
        "편의점",
        "약국",
        "병원",
    ])) {
        score -= 14;
    }

    if (normalizedName.length > normalizedHint.length + 8) {
        score -= 2;
    }

    return score;
};

const pickBestAreaCenterCandidate = (
    areaHint: string,
    results: KakaoKeywordResult[],
): { result: KakaoKeywordResult; score: number } | null => {
    let best: { result: KakaoKeywordResult; score: number } | null = null;

    results.forEach((result) => {
        const score = scoreAreaCenterCandidate(areaHint, result);
        if (!best || score > best.score) {
            best = { result, score };
        }
    });

    return best;
};

const createMarkerImage = (kakao: any, color: string) => {
    const svg = `
        <svg xmlns="http://www.w3.org/2000/svg" width="38" height="50" viewBox="0 0 38 50">
            <path fill="${color}" d="M19 0C8.507 0 0 8.507 0 19c0 14.25 16.15 29.672 18.02 31.418a1.5 1.5 0 0 0 1.96 0C21.85 48.672 38 33.25 38 19 38 8.507 29.493 0 19 0z"/>
            <circle cx="19" cy="19" r="8.5" fill="#ffffff"/>
        </svg>
    `;
    const imageSrc = `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
    const imageSize = new kakao.maps.Size(38, 50);
    const imageOption = { offset: new kakao.maps.Point(19, 50) };
    return new kakao.maps.MarkerImage(imageSrc, imageSize, imageOption);
};

const createCategoryPinOverlayContent = (color: string, label: string) => {
    const wrapper = document.createElement("div");
    wrapper.style.display = "flex";
    wrapper.style.flexDirection = "column";
    wrapper.style.alignItems = "center";
    wrapper.style.cursor = "pointer";
    wrapper.style.userSelect = "none";

    const dot = document.createElement("span");
    dot.style.display = "block";
    dot.style.width = "18px";
    dot.style.height = "18px";
    dot.style.borderRadius = "999px";
    dot.style.border = "2px solid #ffffff";
    dot.style.background = color;
    dot.style.boxShadow = "0 4px 10px rgba(15, 23, 42, 0.25)";

    const tail = document.createElement("span");
    tail.style.display = "block";
    tail.style.width = "0";
    tail.style.height = "0";
    tail.style.borderLeft = "5px solid transparent";
    tail.style.borderRight = "5px solid transparent";
    tail.style.borderTop = `9px solid ${color}`;
    tail.style.marginTop = "-1px";
    tail.style.filter = "drop-shadow(0 3px 6px rgba(15, 23, 42, 0.2))";

    wrapper.append(dot, tail);
    wrapper.title = label;
    wrapper.setAttribute("role", "button");
    wrapper.setAttribute("aria-label", label);
    wrapper.tabIndex = 0;
    return wrapper;
};

const scoreKeywordResult = (
    item: KeywordRecommendation,
    result: KakaoKeywordResult,
    areaHint: string,
) => {
    const targetName = normalizeText(item.name);
    const resultName = normalizeText(result.place_name || "");
    const addressBundle = `${result.road_address_name || ""} ${result.address_name || ""}`;

    let score = 0;
    let sharedNameTokens = 0;

    if (targetName && resultName) {
        if (targetName === resultName) {
            score += 16;
        } else if (resultName.includes(targetName) || targetName.includes(resultName)) {
            score += 11;
        }
    }

    tokenizeText(item.name).forEach((token) => {
        if (result.place_name?.includes(token)) {
            sharedNameTokens += 1;
            score += 2;
        }
    });

    tokenizeText(item.address).slice(0, 5).forEach((token) => {
        if (addressBundle.includes(token)) {
            score += 2;
        }
    });

    tokenizeText(areaHint).slice(0, 3).forEach((token) => {
        if (addressBundle.includes(token) || result.place_name?.includes(token)) {
            score += 1;
        }
    });

    if (sharedNameTokens === 0 && targetName && resultName) {
        score -= 8;
    }

    return score;
};

const pickBestKeywordResult = (
    item: KeywordRecommendation,
    results: KakaoKeywordResult[],
    areaHint: string,
    query: string,
    areaCenter: AreaCenter | null,
): ScoredKeywordResult | null => {
    let best: ScoredKeywordResult | null = null;

    results.forEach((result) => {
        let score = scoreKeywordResult(item, result, areaHint);

        if (areaCenter) {
            const distance = distanceInMeters(
                areaCenter.y,
                areaCenter.x,
                Number(result.y),
                Number(result.x),
            );
            const maxDistance = getMaxAllowedDistance(areaCenter);
            if (distance <= areaCenter.radius) {
                score += 12;
            } else if (distance <= maxDistance) {
                score += 2;
            } else {
                score -= 36;
            }
        }

        if (!best || score > best.score) {
            best = { result, score, query };
        }
    });

    return best;
};

const ensureKakaoServices = async (appKey: string) =>
    new Promise<any>((resolve, reject) => {
        const handleReady = () => {
            if (!window.kakao?.maps) {
                reject(new Error("Kakao Map SDK load failed."));
                return;
            }

            window.kakao.maps.load(() => {
                if (!window.kakao?.maps?.services) {
                    reject(new Error("Kakao Map services library failed to initialize."));
                    return;
                }

                resolve(window.kakao);
            });
        };

        const existingScript = document.querySelector<HTMLScriptElement>(
            'script[data-kakao-map-sdk="true"]',
        );

        if (window.kakao?.maps?.services) {
            resolve(window.kakao);
            return;
        }

        if (window.kakao?.maps) {
            handleReady();
            return;
        }

        if (existingScript) {
            existingScript.addEventListener("load", handleReady, { once: true });
            existingScript.addEventListener("error", () => reject(new Error("Kakao Map SDK load failed.")), { once: true });
            return;
        }

        const script = document.createElement("script");
        script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false&libraries=services`;
        script.async = true;
        script.dataset.kakaoMapSdk = "true";
        script.addEventListener("load", handleReady, { once: true });
        script.addEventListener("error", () => reject(new Error("Kakao Map SDK load failed.")), { once: true });
        document.head.appendChild(script);
    });

const resolveAreaCenter = async (
    kakao: any,
    placesService: any,
    geocoder: any,
    areaHint: string,
    maxDistanceMeters: number,
): Promise<AreaCenter | null> => {
    if (!areaHint.trim()) {
        return null;
    }

    const keywordSearch = (query: string, options?: Record<string, unknown>) =>
        new Promise<KakaoKeywordResult[]>((resolve) => {
            const sanitizedQuery = sanitizeKakaoQuery(query);
            if (!sanitizedQuery) {
                resolve([]);
                return;
            }
            placesService.keywordSearch(
                sanitizedQuery,
                (results: KakaoKeywordResult[], status: string) => {
                    if (status === kakao.maps.services.Status.OK && Array.isArray(results)) {
                        resolve(results);
                        return;
                    }
                    resolve([]);
                },
                options,
            );
        });

    const addressSearch = (query: string) =>
        new Promise<Array<{ x: string; y: string; address_name?: string }>>((resolve) => {
            geocoder.addressSearch(query, (results: Array<{ x: string; y: string; address_name?: string }>, status: string) => {
                if (status === kakao.maps.services.Status.OK && Array.isArray(results)) {
                    resolve(results);
                    return;
                }
                resolve([]);
            });
        });

    const radius = getAreaRadius(areaHint, maxDistanceMeters);
    const maxDistance = Math.min(10000, Math.round(radius * 1.35));
    const areaQueries = uniqueQueries([
        `${areaHint}역`,
        areaHint,
        `${areaHint}동`,
        `${areaHint}사거리`,
    ]);
    const areaKeywordCandidates: KakaoKeywordResult[] = [];
    const seenAreaKeywordKeys = new Set<string>();
    const areaKeywordResults = await Promise.all(
        areaQueries.map((query) => keywordSearch(query, { size: 8 })),
    );
    areaKeywordResults.flat().forEach((result) => {
        const key = `${normalizeText(result.place_name || "")}::${normalizeText(result.address_name || result.road_address_name || "")}`;
        if (seenAreaKeywordKeys.has(key)) {
            return;
        }
        seenAreaKeywordKeys.add(key);
        areaKeywordCandidates.push(result);
    });
    const bestAreaKeyword = pickBestAreaCenterCandidate(areaHint, areaKeywordCandidates);
    if (
        bestAreaKeyword
        && bestAreaKeyword.score >= 8
        && bestAreaKeyword.result.x
        && bestAreaKeyword.result.y
    ) {
        return {
            x: Number(bestAreaKeyword.result.x),
            y: Number(bestAreaKeyword.result.y),
            radius,
            maxDistance,
        };
    }

    const areaAddressResults = await Promise.all(areaQueries.map((query) => addressSearch(query)));
    const areaAddressCandidates = areaAddressResults.flat();
    const normalizedHint = areaHint.replace(/\s+/g, "").toLowerCase();
    const addressResult = areaAddressCandidates.find((candidate) =>
        Boolean(candidate.x && candidate.y)
        && (candidate.address_name || "").replace(/\s+/g, "").toLowerCase().includes(normalizedHint),
    ) || areaAddressCandidates.find((candidate) => Boolean(candidate.x && candidate.y));
    if (addressResult?.x && addressResult?.y) {
        return {
            x: Number(addressResult.x),
            y: Number(addressResult.y),
            radius,
            maxDistance,
        };
    }

    return null;
};

const verifyCategoriesWithKakao = async (
    categories: KeywordRecommendationCategory[],
    areaHint: string,
    appKey: string,
    planHint: string,
    maxDistanceMeters: number,
): Promise<VerificationBundle> => {
    const kakao = await ensureKakaoServices(appKey);
    const placesService = new kakao.maps.services.Places();
    const geocoder = new kakao.maps.services.Geocoder();
    const areaCenter = await resolveAreaCenter(kakao, placesService, geocoder, areaHint, maxDistanceMeters);

    const keywordSearch = (query: string, options?: Record<string, unknown>) =>
        new Promise<KakaoKeywordResult[]>((resolve) => {
            const sanitizedQuery = sanitizeKakaoQuery(query);
            if (!sanitizedQuery) {
                resolve([]);
                return;
            }
            placesService.keywordSearch(
                sanitizedQuery,
                (results: KakaoKeywordResult[], status: string) => {
                    if (status === kakao.maps.services.Status.OK && Array.isArray(results)) {
                        resolve(results);
                        return;
                    }
                    resolve([]);
                },
                options,
            );
        });

    const keywordSearchPagesCache = new Map<string, Promise<KakaoKeywordResult[]>>();

    const keywordSearchPages = async (
        query: string,
        options: Record<string, unknown> = {},
        maxPages = DEFAULT_SEARCH_MAX_PAGES,
    ) => {
        const sanitizedQuery = sanitizeKakaoQuery(query);
        if (!sanitizedQuery) {
            return [];
        }

        const cacheKey = `${sanitizedQuery}::${JSON.stringify(options)}::${maxPages}`;
        const cachedPromise = keywordSearchPagesCache.get(cacheKey);
        if (cachedPromise) {
            return cachedPromise;
        }

        const requestPromise = (async () => {
            const collected: KakaoKeywordResult[] = [];
            const seen = new Set<string>();

            for (let page = 1; page <= maxPages; page += 1) {
                const pageResults = await keywordSearch(sanitizedQuery, { ...options, page });
                if (!pageResults.length) {
                    break;
                }

                pageResults.forEach((result) => {
                    const key = `${normalizeText(result.place_name || "")}::${normalizeText(result.address_name || result.road_address_name || "")}`;
                    if (seen.has(key)) {
                        return;
                    }
                    seen.add(key);
                    collected.push(result);
                });

                if (pageResults.length < DEFAULT_SEARCH_PAGE_SIZE) {
                    break;
                }
            }

            return collected;
        })();

        keywordSearchPagesCache.set(cacheKey, requestPromise);

        const results = await requestPromise;
        return results;
    };

    const resolvedPlaces: Record<string, ResolvedKakaoPlace> = {};
    let droppedCount = 0;
    let replacedCount = 0;
    const verifiedCategories: KeywordRecommendationCategory[] = [];
    const usedVenueKeys = new Set<string>();
    const normalizedPlanHint = normalizeHint(planHint);

    for (const category of categories) {
        const nextPlaces: KeywordRecommendation[] = [];

        for (const item of category.places) {
            const queries = uniqueQueries([
                item.mapQuery,
                buildMapQuery(item.name, item.address, areaHint),
                [item.name, areaHint].filter(Boolean).join(" ").trim(),
                item.name,
            ]).slice(0, ITEM_QUERY_LIMIT);

            const searchOptions = areaCenter
                ? {
                    x: areaCenter.x,
                    y: areaCenter.y,
                    radius: areaCenter.radius,
                    sort: kakao.maps.services.SortBy.DISTANCE,
                    size: DEFAULT_SEARCH_PAGE_SIZE,
                }
                : { size: DEFAULT_SEARCH_PAGE_SIZE };
            const candidateMatches = await Promise.all(
                queries.map(async (query) => {
                    const results = await keywordSearchPages(query, searchOptions, DEFAULT_SEARCH_MAX_PAGES);
                    return pickBestKeywordResult(
                        item,
                        results.slice(0, 20),
                        areaHint,
                        query,
                        areaCenter,
                    );
                }),
            );
            const bestMatch = candidateMatches.reduce<ScoredKeywordResult | null>((best, candidate) => {
                if (!candidate) {
                    return best;
                }
                if (!best || candidate.score > best.score) {
                    return candidate;
                }
                return best;
            }, null);

            if (!bestMatch || bestMatch.score < MATCH_THRESHOLD) {
                droppedCount += 1;
                continue;
            }

            if (areaCenter) {
                const dist = distanceInMeters(
                    areaCenter.y,
                    areaCenter.x,
                    Number(bestMatch.result.y),
                    Number(bestMatch.result.x),
                );
                if (dist > getMaxAllowedDistance(areaCenter)) {
                    droppedCount += 1;
                    continue;
                }
            }

            if (!hasNameTokenOverlap(item.name, bestMatch.result.place_name || "")) {
                droppedCount += 1;
                continue;
            }

            if (!matchesCategoryIntent(category.key, bestMatch.result, planHint, normalizedPlanHint)) {
                droppedCount += 1;
                continue;
            }

            const matched = bestMatch.result;
            const verifiedAddress = matched.road_address_name || matched.address_name || item.address;
            const venueKey = `${normalizeText(matched.place_name || item.name)}::${normalizeText(verifiedAddress)}`;
            if (usedVenueKeys.has(venueKey)) {
                continue;
            }

            const verifiedItem: KeywordRecommendation = {
                ...item,
                name: matched.place_name || item.name,
                address: verifiedAddress,
                mapQuery: buildMapQuery(matched.place_name || item.name, verifiedAddress, areaHint),
            };

            const placeKey = buildPlaceKey(verifiedItem);
            usedVenueKeys.add(venueKey);
            resolvedPlaces[placeKey] = {
                placeKey,
                lat: Number(matched.y),
                lng: Number(matched.x),
                detailUrl: matched.place_url || `https://map.kakao.com/?q=${encodeURIComponent(verifiedItem.mapQuery || verifiedItem.name)}`,
                displayName: matched.place_name || verifiedItem.name,
                roadAddress: matched.road_address_name || verifiedAddress,
                addressName: matched.address_name || verifiedAddress,
                phone: matched.phone,
                categoryName: matched.category_name,
                matchedQuery: bestMatch.query,
            };
            nextPlaces.push(verifiedItem);
        }

        if (nextPlaces.length < CATEGORY_PLACE_TARGET) {
            const replacementQueries = buildCategoryQueries(category.key, areaHint, planHint)
                .slice(0, REPLACEMENT_QUERY_LIMIT);

            for (const query of replacementQueries) {
                if (nextPlaces.length >= CATEGORY_PLACE_TARGET) {
                    break;
                }

                const results = await keywordSearchPages(
                    query,
                    areaCenter
                        ? {
                            x: areaCenter.x,
                            y: areaCenter.y,
                            radius: areaCenter.radius,
                            sort: kakao.maps.services.SortBy.DISTANCE,
                            size: DEFAULT_SEARCH_PAGE_SIZE,
                        }
                        : { size: DEFAULT_SEARCH_PAGE_SIZE },
                    DEFAULT_SEARCH_MAX_PAGES,
                );

                for (const result of results) {
                    if (nextPlaces.length >= CATEGORY_PLACE_TARGET) {
                        break;
                    }

                    if (!matchesCategoryIntent(category.key, result, planHint, normalizedPlanHint)) {
                        continue;
                    }

                    if (areaCenter) {
                        const dist = distanceInMeters(
                            areaCenter.y,
                            areaCenter.x,
                            Number(result.y),
                            Number(result.x),
                        );
                        if (dist > getMaxAllowedDistance(areaCenter)) {
                            continue;
                        }
                    }

                    const address = result.road_address_name || result.address_name;
                    if (!address) {
                        continue;
                    }

                    const venueKey = `${normalizeText(result.place_name || "")}::${normalizeText(address)}`;
                    if (usedVenueKeys.has(venueKey)) {
                        continue;
                    }

                    const replacementItem: KeywordRecommendation = {
                        category: category.key,
                        name: result.place_name,
                        address,
                        reason: buildDefaultReason(category.key, areaHint, planHint),
                        tags: buildDefaultTags(category.key, planHint),
                        mapQuery: buildMapQuery(result.place_name, address, areaHint),
                    };
                    const placeKey = buildPlaceKey(replacementItem);

                    usedVenueKeys.add(venueKey);
                    resolvedPlaces[placeKey] = {
                        placeKey,
                        lat: Number(result.y),
                        lng: Number(result.x),
                        detailUrl: result.place_url || `https://map.kakao.com/?q=${encodeURIComponent(replacementItem.mapQuery || replacementItem.name)}`,
                        displayName: result.place_name,
                        roadAddress: result.road_address_name || address,
                        addressName: result.address_name || address,
                        phone: result.phone,
                        categoryName: result.category_name,
                        matchedQuery: query,
                    };

                    replacedCount += 1;
                    nextPlaces.push(replacementItem);
                }
            }
        }

        verifiedCategories.push({
            ...category,
            places: nextPlaces.slice(0, CATEGORY_PLACE_TARGET),
        });
    }

    return {
        categories: sortCategories(verifiedCategories),
        resolvedPlaces,
        droppedCount,
        replacedCount,
    };
};

export default function ResultPage() {
    const routerLocation = useLocation();
    const navigate = useNavigate();
    const mapRef = useRef<HTMLDivElement | null>(null);
    const routeState = (routerLocation.state || {}) as RouteState;

    const searchParams = useMemo(
        () => new URLSearchParams(routerLocation.search),
        [routerLocation.search],
    );

    const keyword = searchParams.get("keyword")?.trim()
        || routeState.location
        || routeState.mood
        || routeState.meetingType
        || "";
    const meetingType = searchParams.get("meetingType")?.trim() || routeState.meetingType || "";
    const mood = searchParams.get("mood")?.trim() || routeState.mood || "";
    const preferredLocation = searchParams.get("location")?.trim() || routeState.location || "";
    const planHint = searchParams.get("planHint")?.trim() || routeState.planHint || "";
    const areaHint = useMemo(
        () => resolveAreaHint(preferredLocation, keyword, planHint),
        [preferredLocation, keyword, planHint],
    );
    const maxDistanceKm = useMemo(
        () => resolveAdaptiveDistanceKm(areaHint || preferredLocation, planHint),
        [areaHint, preferredLocation, planHint],
    );
    const maxDistanceMeters = useMemo(
        () => Math.round(maxDistanceKm * 1000),
        [maxDistanceKm],
    );

    const requestParams = useMemo(
        () => ({
            keyword,
            meetingType,
            mood,
            location: areaHint,
            planHint,
        }),
        [keyword, meetingType, mood, areaHint, planHint],
    );

    const cacheKey = useMemo(
        () => buildRecommendationCacheKey(requestParams),
        [requestParams],
    );

    const initialCache = useMemo(
        () => (keyword ? loadRecommendationCache(cacheKey) : null),
        [cacheKey, keyword],
    );

    const [categories, setCategories] = useState<KeywordRecommendationCategory[]>(
        initialCache?.categories || [],
    );
    const [selectedPlaces, setSelectedPlaces] = useState<Record<string, KeywordRecommendation>>(
        initialCache?.selectedPlaces || {},
    );
    const [resolvedPlaces, setResolvedPlaces] = useState<Record<string, ResolvedKakaoPlace>>(
        initialCache?.resolvedPlaces || {},
    );
    const [loading, setLoading] = useState(!initialCache && Boolean(keyword));
    const [error, setError] = useState("");
    const [warning, setWarning] = useState(initialCache?.warning || "");
    const [mapWarning, setMapWarning] = useState("");
    const [shareMessage, setShareMessage] = useState("");
    const [recommendationSource, setRecommendationSource] = useState(initialCache?.source || "");
    const [isCachedView, setIsCachedView] = useState(Boolean(initialCache));
    const [refreshNonce, setRefreshNonce] = useState(0);

    const visibleCategories = useMemo(
        () => categories.filter((category) => category.places.length > 0),
        [categories],
    );

    const selectedPlaceList = useMemo(
        () =>
            visibleCategories
                .map((category) => selectedPlaces[category.key])
                .filter((item): item is KeywordRecommendation => Boolean(item)),
        [visibleCategories, selectedPlaces],
    );

    const mapPlaceCandidates = useMemo(() => {
        const selectedEntries = visibleCategories
            .map((category) => {
                const selected = selectedPlaces[category.key];
                if (!selected) {
                    return null;
                }
                return {
                    item: selected,
                    categoryKey: category.key,
                };
            })
            .filter(
                (
                    entry,
                ): entry is {
                    item: KeywordRecommendation;
                    categoryKey: RecommendationCategoryKey;
                } => Boolean(entry),
            );

        if (selectedEntries.length > 0) {
            return selectedEntries;
        }

        return visibleCategories
            .map((category) => {
                const first = category.places[0];
                if (!first) {
                    return null;
                }
                return {
                    item: first,
                    categoryKey: category.key,
                };
            })
            .filter(
                (
                    entry,
                ): entry is {
                    item: KeywordRecommendation;
                    categoryKey: RecommendationCategoryKey;
                } => Boolean(entry),
            );
    }, [visibleCategories, selectedPlaces]);

    const mapPlaces = useMemo(
        () => mapPlaceCandidates.map((entry) => entry.item),
        [mapPlaceCandidates],
    );

    const mappedPlaceEntries = useMemo(
        () =>
            mapPlaceCandidates
                .map(({ item, categoryKey }) => ({
                    item,
                    categoryKey,
                    resolved: resolvedPlaces[buildPlaceKey(item)],
                }))
                .filter(
                    (
                        entry,
                    ): entry is {
                        item: KeywordRecommendation;
                        categoryKey: RecommendationCategoryKey;
                        resolved: ResolvedKakaoPlace;
                    } => Boolean(entry.resolved),
                ),
        [mapPlaceCandidates, resolvedPlaces],
    );

    const hasRecommendations = visibleCategories.length > 0;
    const totalPlaces = useMemo(
        () => visibleCategories.reduce((sum, category) => sum + category.places.length, 0),
        [visibleCategories],
    );
    const mapLegendCategories = useMemo(
        () =>
            visibleCategories.map((category) => ({
                key: category.key,
                title: category.title || formatCategoryTitle(category.key),
            })),
        [visibleCategories],
    );
    const headlineArea = areaHint || preferredLocation || "";
    const meetingSummary = [meetingType || "모임", mood, headlineArea]
        .filter(Boolean)
        .join(" · ");
    const headlineTitle = headlineArea ? `${headlineArea} 실제 장소 추천` : "실제 장소 추천";

    useEffect(() => {
        if (!keyword) {
            setCategories([]);
            setSelectedPlaces({});
            setResolvedPlaces({});
            setError("추천에 필요한 키워드가 없습니다.");
            setWarning("");
            setRecommendationSource("");
            setLoading(false);
            setIsCachedView(false);
            return;
        }

        const cachedEntry = loadRecommendationCache(cacheKey);
        if (cachedEntry && refreshNonce === 0) {
            setCategories(cachedEntry.categories || []);
            setSelectedPlaces(cachedEntry.selectedPlaces || buildInitialSelections(cachedEntry.categories || []));
            setResolvedPlaces(cachedEntry.resolvedPlaces || {});
            setRecommendationSource(cachedEntry.source || "");
            setWarning(cachedEntry.warning || "");
            setError("");
            setLoading(false);
            setIsCachedView(true);
            return;
        }

        let active = true;

        const loadRecommendations = async () => {
            setLoading(true);
            setError("");
            setWarning("");
            setMapWarning("");
            setShareMessage("");
            setIsCachedView(false);

            try {
                const rawResult = await recommendByKeyword(requestParams);
                const result = normalizeKeywordRecommendResult(rawResult);
                if (!active) {
                    return;
                }

                const rawCategories = sortCategories(result.categories);
                if (!rawCategories.length) {
                    setCategories([]);
                    setSelectedPlaces({});
                    setResolvedPlaces({});
                    setRecommendationSource(result.source || "");
                    setWarning(result.warning || "");
                    setError("추천 결과가 비어 있습니다. 조건을 조금 더 구체적으로 입력해 주세요.");
                    return;
                }

                const appKey = import.meta.env.VITE_KAKAO_MAP_API_KEY;

                if (!appKey) {
                    throw new Error("Kakao Map API 키가 설정되어 있지 않아 실재 장소 검증을 할 수 없습니다.");
                }

                const verification = await verifyCategoriesWithKakao(
                    rawCategories,
                    areaHint,
                    appKey,
                    planHint,
                    maxDistanceMeters,
                );

                if (!active) {
                    return;
                }

                if (!verification || !Array.isArray(verification.categories)) {
                    throw new Error("추천 장소 검증 결과를 해석하지 못했습니다. 다시 시도해 주세요.");
                }

                if (!verification.categories.length) {
                    setCategories([]);
                    setSelectedPlaces({});
                    setResolvedPlaces({});
                    setRecommendationSource(result.source || "");
                    setError("카카오맵에서 확인되는 실제 장소를 찾지 못했습니다. 검색어를 더 구체적으로 입력해 주세요.");
                    setWarning(result.warning || "");
                    return;
                }

                const nextSelections = buildInitialSelections(verification.categories);
                const warningMessages = [
                    result.warning,
                    // result.source !== "AI" ? "AI 추천이 아니라 대체 추천이 표시되고 있습니다." : "",
                    // verification.replacedCount > 0
                    //     ? `GPT가 낸 후보 중 ${verification.replacedCount}건은 실제 Kakao 장소로 교체했습니다.`
                    //     : "",
                    // verification.droppedCount > 0
                    //     ? `실재 장소로 교체하지 못한 후보 ${verification.droppedCount}건은 제외했습니다.`
                    //     : "",
                ].filter(Boolean);
                const nextWarning = warningMessages.join(" ");

                setCategories(verification.categories);
                setSelectedPlaces(nextSelections);
                setResolvedPlaces(verification.resolvedPlaces);
                setRecommendationSource(result.source || "");
                setWarning(nextWarning);

                const cacheEntry: RecommendationCacheEntry = {
                    categories: verification.categories,
                    routes: result.routes || [],
                    source: result.source || "",
                    warning: nextWarning,
                    selectedPlaces: nextSelections,
                    resolvedPlaces: verification.resolvedPlaces,
                    savedAt: Date.now(),
                };
                saveRecommendationCache(cacheKey, cacheEntry);
            } catch (fetchError) {
                if (!active) {
                    return;
                }

                setCategories([]);
                setSelectedPlaces({});
                setResolvedPlaces({});

                if (axios.isAxiosError(fetchError)) {
                    const message = fetchError.response?.data?.message;
                    setError(message || "추천 장소를 불러오지 못했습니다.");
                } else if (fetchError instanceof Error) {
                    setError(fetchError.message || "추천 장소를 불러오지 못했습니다.");
                } else {
                    setError("추천 장소를 불러오지 못했습니다.");
                }
            } finally {
                if (active) {
                    setLoading(false);
                }
            }
        };

        void loadRecommendations();

        return () => {
            active = false;
        };
    }, [areaHint, cacheKey, keyword, maxDistanceMeters, refreshNonce, requestParams, planHint]);

    useEffect(() => {
        if (!hasRecommendations) {
            return;
        }

        saveRecommendationCache(cacheKey, {
            categories,
            routes: [],
            source: recommendationSource,
            warning,
            selectedPlaces,
            resolvedPlaces,
            savedAt: Date.now(),
        });
    }, [
        cacheKey,
        categories,
        hasRecommendations,
        recommendationSource,
        resolvedPlaces,
        selectedPlaces,
        warning,
    ]);

    useEffect(() => {
        if (!mapRef.current) {
            return;
        }

        if (!mappedPlaceEntries.length) {
            setMapWarning(hasRecommendations ? "카카오맵에서 확인된 장소만 표시할 수 있습니다." : "");
            return;
        }

        const appKey = import.meta.env.VITE_KAKAO_MAP_API_KEY;
        if (!appKey) {
            setMapWarning("Kakao Map API 키가 설정되어 있지 않습니다.");
            return;
        }

        let cancelled = false;

        ensureKakaoServices(appKey)
            .then(async (kakao) => {
                if (cancelled || !mapRef.current) {
                    return;
                }

                const placesService = new kakao.maps.services.Places();
                const geocoder = new kakao.maps.services.Geocoder();
                const areaCenter = await resolveAreaCenter(
                    kakao,
                    placesService,
                    geocoder,
                    areaHint,
                    maxDistanceMeters,
                );

                if (cancelled || !mapRef.current) {
                    return;
                }

                const map = new kakao.maps.Map(mapRef.current, {
                    center: areaCenter
                        ? new kakao.maps.LatLng(areaCenter.y, areaCenter.x)
                        : new kakao.maps.LatLng(37.5665, 126.978),
                    level: 6,
                });
                const bounds = new kakao.maps.LatLngBounds();
                const markerImageCache = new Map<string, any>();
                let activeInfoWindow: any | null = null;
                const closeActiveInfoWindow = () => {
                    if (activeInfoWindow) {
                        activeInfoWindow.close();
                        activeInfoWindow = null;
                    }
                };

                kakao.maps.event.addListener(map, "click", () => {
                    closeActiveInfoWindow();
                });

                const entriesForMap = areaCenter
                    ? mappedPlaceEntries.filter(({ resolved }) => {
                        const distance = distanceInMeters(
                            areaCenter.y,
                            areaCenter.x,
                            resolved.lat,
                            resolved.lng,
                        );
                        return distance <= getMaxAllowedDistance(areaCenter);
                    })
                    : mappedPlaceEntries;
                const finalEntries = entriesForMap.length > 0 ? entriesForMap : mappedPlaceEntries;

                finalEntries.forEach(({ item, categoryKey, resolved }) => {
                    const position = new kakao.maps.LatLng(resolved.lat, resolved.lng);
                    const markerColor = getCategoryPinColor(categoryKey);
                    if (!markerImageCache.has(markerColor)) {
                        markerImageCache.set(markerColor, createMarkerImage(kakao, markerColor));
                    }
                    const marker = new kakao.maps.Marker({
                        map,
                        position,
                        image: markerImageCache.get(markerColor),
                    });
                    if (typeof marker.setOpacity === "function") {
                        marker.setOpacity(0);
                    }

                    const overlayContent = createCategoryPinOverlayContent(
                        markerColor,
                        `${formatCategoryTitle(categoryKey)} · ${resolved.displayName}`,
                    );
                    const markerOverlay = new kakao.maps.CustomOverlay({
                        position,
                        content: overlayContent,
                        xAnchor: 0.5,
                        yAnchor: 1,
                    });
                    markerOverlay.setMap(map);

                    bounds.extend(position);

                    const infoWindow = new kakao.maps.InfoWindow({
                        removable: true,
                        content: `
                          <div style="padding:14px 16px;max-width:280px;line-height:1.6;">
                            <strong style="display:block;margin-bottom:6px;font-size:15px;">${escapeHtml(resolved.displayName)}</strong>
                            <div style="font-size:12px;color:#475569;">${escapeHtml(resolved.roadAddress || resolved.addressName || item.address)}</div>
                            ${
                                resolved.phone
                                    ? `<div style="margin-top:4px;font-size:12px;color:#64748b;">${escapeHtml(resolved.phone)}</div>`
                                    : ""
                            }
                            <a
                              href="${escapeHtml(resolved.detailUrl)}"
                              target="_blank"
                              rel="noopener noreferrer"
                              style="display:inline-block;margin-top:10px;padding:7px 10px;border-radius:999px;background:#0f172a;color:#fff;text-decoration:none;font-size:12px;font-weight:600;"
                            >카카오맵 상세 보기</a>
                          </div>
                        `,
                    });

                    const toggleInfoWindow = () => {
                        if (activeInfoWindow === infoWindow) {
                            closeActiveInfoWindow();
                            return false;
                        }
                        closeActiveInfoWindow();
                        infoWindow.open(map, marker);
                        activeInfoWindow = infoWindow;
                        return true;
                    };

                    kakao.maps.event.addListener(marker, "click", toggleInfoWindow);
                    overlayContent.addEventListener("click", (event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        toggleInfoWindow();
                    });
                    overlayContent.addEventListener("keydown", (event) => {
                        if (event.key === "Enter" || event.key === " ") {
                            event.preventDefault();
                            event.stopPropagation();
                            toggleInfoWindow();
                        }
                    });
                });

                if (finalEntries.length === 1) {
                    const only = finalEntries[0].resolved;
                    map.setCenter(new kakao.maps.LatLng(only.lat, only.lng));
                    map.setLevel(4);
                } else {
                    map.setBounds(bounds);
                }

                const missingCount = mapPlaces.length - mappedPlaceEntries.length;
                const outOfAreaCount = mappedPlaceEntries.length - finalEntries.length;
                const warnings: string[] = [];
                if (missingCount > 0) {
                    warnings.push(`선택한 장소 ${missingCount}건은 카카오맵 등록 정보를 찾지 못해 지도에서 제외했습니다.`);
                }
                if (outOfAreaCount > 0 && areaHint) {
                    warnings.push(
                        `${areaHint} 중심 ${formatDistanceKm(maxDistanceKm)}km 범위를 벗어난 장소 ${outOfAreaCount}건은 지도에서 제외했습니다.`,
                    );
                }
                if (warnings.length > 0) {
                    setMapWarning(warnings.join(" "));
                } else {
                    setMapWarning("");
                }
            })
            .catch(() => {
                if (!cancelled) {
                    setMapWarning("Kakao Map SDK를 초기화하지 못했습니다. 도메인 설정과 JavaScript 키를 확인해 주세요.");
                }
            });

        return () => {
            cancelled = true;
        };
    }, [areaHint, hasRecommendations, mapPlaces.length, mappedPlaceEntries, maxDistanceKm, maxDistanceMeters]);

    const handleSelectPlace = (categoryKey: RecommendationCategoryKey, item: KeywordRecommendation) => {
        setSelectedPlaces((current) => ({
            ...current,
            [categoryKey]: item,
        }));
    };

    const handleRefresh = () => {
        if (loading) {
            return;
        }
        clearRecommendationCache(cacheKey);
        setCategories([]);
        setSelectedPlaces({});
        setResolvedPlaces({});
        setWarning("");
        setError("");
        setMapWarning("");
        setShareMessage("");
        setRecommendationSource("");
        setIsCachedView(false);
        setLoading(true);
        setRefreshNonce((current) => current + 1);
    };

    const handleShare = async () => {
        const shareUrl = window.location.href;
        const shareData = {
            title: "Meeting Mate 추천 결과",
            text: `${meetingSummary} 추천 결과`,
            url: shareUrl,
        };

        try {
            if (navigator.share) {
                await navigator.share(shareData);
                setShareMessage("추천 결과 공유 창을 열었습니다.");
                return;
            }

            await navigator.clipboard.writeText(shareUrl);
            setShareMessage("추천 결과 링크를 클립보드에 복사했습니다.");
        } catch {
            setShareMessage("공유를 완료하지 못했습니다. 브라우저 설정을 확인해 주세요.");
        }
    };

    const handleOpenSelectedRoutePage = () => {
        if (selectedPlaceList.length < 2) {
            setShareMessage("다음 단계로 가려면 최소 2개의 장소를 선택해 주세요.");
            return;
        }

        navigate("/selected-route", {
            state: {
                keyword,
                meetingType,
                mood,
                location: areaHint,
                planHint,
                selectedPlaces: selectedPlaceList,
            },
        });
    };

    return (
        <div className="min-h-screen bg-stone-50 text-slate-900">
            <Header />

            <main className="mx-auto max-w-[1500px] px-4 pb-20 pt-10 md:px-6">
                <section className="rounded-[32px] border border-slate-200 bg-white px-6 py-7 shadow-sm md:px-8">
                    <div className="flex flex-col gap-6 xl:flex-row xl:items-end xl:justify-between">
                        <div className="max-w-4xl">
                            <p className="text-sm uppercase tracking-[0.28em] text-slate-500">Verified Places</p>
                            <h2 className="mt-3 break-keep text-3xl font-semibold leading-tight text-slate-900 md:text-4xl">
                                {headlineTitle}
                            </h2>
                            <p className="mt-4 text-sm leading-6 text-slate-600 md:text-base">
                                지역 중심으로 가까운 실제 장소 후보만 정리했습니다.
                            </p>
                            <div className="mt-5 flex flex-wrap gap-2">
                                <span className="rounded-full bg-slate-900 px-3 py-1 text-xs font-semibold text-white">
                                    {meetingType || "모임"}
                                </span>
                                {mood && (
                                    <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                                        {mood}
                                    </span>
                                )}
                                <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-700">
                                    {headlineArea || "지역 미지정"}
                                </span>
                            </div>
                        </div>

                        <div className="grid gap-3 sm:grid-cols-2 xl:min-w-[420px] xl:grid-cols-2">
                            <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                                <p className="text-xs uppercase tracking-[0.2em] text-slate-400">카테고리 / 후보</p>
                                <p className="mt-2 text-sm font-medium text-slate-900">
                                    {visibleCategories.length}개 / {totalPlaces}곳
                                </p>
                            </div>
                            <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                                <p className="text-xs uppercase tracking-[0.2em] text-slate-400">추천 소스</p>
                                <p className="mt-2 text-sm font-medium text-slate-900">
                                    {formatRecommendationSource(recommendationSource)}
                                </p>
                            </div>
                            <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                                <p className="text-xs uppercase tracking-[0.2em] text-slate-400">캐시 상태</p>
                                <p className="mt-2 text-sm font-medium text-slate-900">
                                    {isCachedView ? "저장된 결과" : "새로 검증한 결과"}
                                </p>
                            </div>
                            <Button variant="secondary" onClick={handleRefresh} className="h-full">
                                다시 검색하기
                            </Button>
                        </div>
                    </div>
                </section>

                {warning && (
                    <p className="mt-6 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-700">
                        {warning}
                    </p>
                )}
                {error && (
                    <p className="mt-6 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700">
                        {error}
                    </p>
                )}
                {shareMessage && (
                    <p className="mt-6 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm leading-6 text-emerald-700">
                        {shareMessage}
                    </p>
                )}

                {loading && !hasRecommendations && (
                    <section className="mt-8 grid gap-6 xl:grid-cols-[minmax(0,1.4fr)_420px]">
                        <div className="space-y-6">
                            <div className="grid gap-4 lg:grid-cols-2">
                                <div className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                                    <div className="h-4 w-24 rounded-full bg-slate-200" />
                                    <div className="mt-4 h-10 w-56 max-w-full rounded-full bg-slate-200" />
                                    <div className="mt-5 space-y-3">
                                        <div className="h-28 rounded-3xl bg-slate-100" />
                                        <div className="h-28 rounded-3xl bg-slate-100" />
                                    </div>
                                </div>
                                <div className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                                    <div className="h-4 w-24 rounded-full bg-slate-200" />
                                    <div className="mt-4 h-10 w-56 max-w-full rounded-full bg-slate-200" />
                                    <div className="mt-5 space-y-3">
                                        <div className="h-28 rounded-3xl bg-slate-100" />
                                        <div className="h-28 rounded-3xl bg-slate-100" />
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                            <div className="h-4 w-24 rounded-full bg-slate-200" />
                            <div className="mt-4 h-10 w-60 rounded-full bg-slate-200" />
                            <div className="mt-5 h-[620px] rounded-[24px] bg-slate-100" />
                        </div>
                    </section>
                )}

                {hasRecommendations && (
                    <section className="mt-8 grid gap-6 xl:grid-cols-[minmax(0,1.42fr)_420px]">
                        <div className="space-y-6">
                            <div className="grid gap-4 2xl:grid-cols-2">
                                {visibleCategories.map((category) => (
                                    <article
                                        key={category.key}
                                        className={`rounded-[28px] border p-5 shadow-sm ${CATEGORY_STYLE[category.key] || "border-slate-200 bg-white"}`}
                                    >
                                        <div className="flex items-start justify-between gap-4">
                                            <div>
                                                <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${CATEGORY_BADGE[category.key] || "bg-slate-200 text-slate-700"}`}>
                                                    {category.title || formatCategoryTitle(category.key)}
                                                </span>
                                                <h3 className="mt-3 break-keep text-2xl font-semibold leading-tight text-slate-900">
                                                    {category.title || formatCategoryTitle(category.key)}
                                                </h3>
                                                {category.description && (
                                                    <p className="mt-2 text-sm leading-6 text-slate-600">
                                                        {category.description}
                                                    </p>
                                                )}
                                            </div>
                                            <div className="rounded-2xl bg-white/80 px-3 py-2 text-right">
                                                <p className="text-xs uppercase tracking-[0.2em] text-slate-400">후보</p>
                                                <p className="mt-1 text-lg font-semibold text-slate-900">{category.places.length}</p>
                                            </div>
                                        </div>

                                        <div className="mt-5 grid gap-3 sm:grid-cols-2">
                                            {category.places.map((item, index) => {
                                                const selected = selectedPlaces[category.key]?.name === item.name
                                                    && selectedPlaces[category.key]?.address === item.address;
                                                const resolved = resolvedPlaces[buildPlaceKey(item)];

                                                return (
                                                    <div
                                                        key={`${category.key}-${item.name}-${index}`}
                                                        className={`rounded-[24px] border p-4 transition-all ${
                                                            selected
                                                                ? "border-slate-900 bg-white shadow-[0_18px_36px_rgba(15,23,42,0.08)]"
                                                                : "border-white/80 bg-white/75"
                                                        }`}
                                                    >
                                                        <div className="flex items-start justify-between gap-3">
                                                            <div className="min-w-0 flex-1">
                                                                <p className="text-[11px] uppercase tracking-[0.24em] text-slate-400">
                                                                    {category.title || formatCategoryTitle(category.key)} {index + 1}
                                                                </p>
                                                                <p className="mt-2 break-keep text-lg font-semibold leading-snug text-slate-900">
                                                                    {item.name}
                                                                </p>
                                                            </div>
                                                            <button
                                                                type="button"
                                                                onClick={() => handleSelectPlace(category.key, item)}
                                                                className={`shrink-0 rounded-full px-3 py-2 text-xs font-semibold transition ${
                                                                    selected
                                                                        ? "bg-slate-900 text-white"
                                                                        : "bg-slate-100 text-slate-700 hover:bg-slate-200"
                                                                }`}
                                                            >
                                                                {selected ? "선택됨" : "선택"}
                                                            </button>
                                                        </div>

                                                        <p className="mt-2 text-sm leading-6 text-slate-500">
                                                            {item.address}
                                                        </p>

                                                        {item.reason && (
                                                            <p className="mt-3 text-sm leading-6 text-slate-600">
                                                                {item.reason}
                                                            </p>
                                                        )}

                                                        {!!item.tags?.length && (
                                                            <div className="mt-3 flex flex-wrap gap-2">
                                                                {item.tags.slice(0, 4).map((tag) => (
                                                                    <span
                                                                        key={`${item.name}-${tag}`}
                                                                        className="rounded-full bg-slate-900 px-2.5 py-1 text-[11px] font-medium text-white"
                                                                    >
                                                                        {tag}
                                                                    </span>
                                                                ))}
                                                            </div>
                                                        )}

                                                        <div className="mt-4 flex flex-wrap gap-2">
                                                            {resolved ? (
                                                                <a
                                                                    href={resolved.detailUrl}
                                                                    target="_blank"
                                                                    rel="noopener noreferrer"
                                                                    className="inline-flex rounded-full border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50"
                                                                >
                                                                    카카오맵 상세 확인
                                                                </a>
                                                            ) : (
                                                                <>
                                                                    <a
                                                                        href={`https://map.kakao.com/?q=${encodeURIComponent(item.mapQuery || item.name)}`}
                                                                        target="_blank"
                                                                        rel="noopener noreferrer"
                                                                        className="inline-flex rounded-full border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50"
                                                                    >
                                                                        카카오맵 검색
                                                                    </a>
                                                                    <span className="inline-flex rounded-full border border-dashed border-slate-300 px-3 py-2 text-xs font-semibold text-slate-500">
                                                                        지도 미검증
                                                                    </span>
                                                                </>
                                                            )}
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </article>
                                ))}
                            </div>
                        </div>

                        <aside className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm lg:sticky lg:top-6 lg:self-start">
                            <p className="text-sm font-medium text-slate-500">Kakao Map</p>
                            <h3 className="mt-2 max-w-sm break-keep text-2xl font-semibold leading-tight text-slate-900">
                                선택한 장소를 지도에서 바로 확인
                            </h3>
                            <p className="mt-3 max-w-xl text-sm leading-6 text-slate-500">
                                핀을 누르면 카카오맵 상세 보기가 열립니다. 지도에는 선택한 장소와 각 카테고리 대표 후보를 우선 표시합니다.
                            </p>
                            {mapLegendCategories.length > 0 && (
                                <div className="mt-4 flex flex-wrap gap-2">
                                    {mapLegendCategories.map((category) => (
                                        <span
                                            key={`legend-${category.key}`}
                                            className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-semibold text-slate-700"
                                        >
                                            <span
                                                className="h-2.5 w-2.5 rounded-full"
                                                style={{ backgroundColor: getCategoryPinColor(category.key) }}
                                            />
                                            {category.title}
                                        </span>
                                    ))}
                                </div>
                            )}

                            <div
                                ref={mapRef}
                                className="mt-5 h-[620px] w-full rounded-[24px] bg-slate-100"
                            />

                            {mapWarning && (
                                <p className="mt-4 rounded-2xl border border-orange-200 bg-orange-50 px-4 py-3 text-sm leading-6 text-orange-700">
                                    {mapWarning}
                                </p>
                            )}
                        </aside>
                    </section>
                )}

                {!loading && !hasRecommendations && !error && (
                    <section className="mt-8 rounded-[28px] border border-slate-200 bg-white p-8 shadow-sm">
                        <p className="text-sm font-medium text-slate-500">추천 대기</p>
                        <h3 className="mt-2 break-keep text-2xl font-semibold text-slate-900">
                            아직 표시할 추천 결과가 없습니다.
                        </h3>
                        <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600">
                            같은 검색 조건의 저장 결과가 없거나, 카카오맵에서 확인 가능한 장소를 아직 찾지 못했습니다.
                        </p>
                    </section>
                )}

                <div className="mt-10 flex flex-wrap items-center justify-between gap-4 rounded-[28px] border border-slate-200 bg-white px-6 py-5 shadow-sm">
                    <div>
                        <p className="text-sm font-medium text-slate-500">다음 단계</p>
                        <p className="mt-1 text-base text-slate-900">
                            고른 장소만 모아 다음 페이지에서 전용 플랜을 다시 추천받을 수 있습니다.
                        </p>
                    </div>
                    <div className="flex flex-wrap gap-3">
                        <Button onClick={handleOpenSelectedRoutePage}>
                            선택한 장소로 다음 단계
                        </Button>
                        <Button variant="secondary" onClick={handleShare}>
                            공유하기
                        </Button>
                    </div>
                </div>
            </main>
        </div>
    );
}
