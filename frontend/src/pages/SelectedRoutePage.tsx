import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import { useLocation, useNavigate } from "react-router-dom";
import { recommendRoutesFromSelections } from "../api/recommendation";
import Button from "../components/Button";
import Header from "../components/Header";
import type {
    KeywordRecommendation,
    KeywordRecommendationRoute,
    RecommendationCategoryKey,
} from "../types/recommendation";

const CATEGORY_ORDER: RecommendationCategoryKey[] = ["PC_ROOM", "RESTAURANT", "CAFE", "BAR", "KARAOKE", "SHOPPING", "ACTIVITY"];

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

const formatSource = (source: string) => {
    if (source === "AI") return "AI";
    if (source === "FALLBACK") return "기본 재조합";
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

const clamp = (value: number) => Math.max(0, Math.min(100, value));

const deriveRouteMetrics = (route: KeywordRecommendationRoute) => {
    let conversation = 35;
    let energy = 30;
    let stretch = 35;

    route.steps.forEach((step, index) => {
        if (step.category === "RESTAURANT") {
            conversation += 16;
            stretch += 8;
        }
        if (step.category === "CAFE") {
            conversation += 20;
            stretch += 12;
        }
        if (step.category === "BAR") {
            energy += 18;
            stretch += 22;
        }
        if (step.category === "PC_ROOM") {
            energy += 24;
            conversation -= 4;
        }
        if (step.category === "KARAOKE") {
            energy += 20;
            stretch += 16;
        }
        if (step.category === "SHOPPING") {
            conversation += 8;
            stretch += 10;
        }
        if (step.category === "ACTIVITY") {
            energy += 18;
        }
        if (index === route.steps.length - 1 && step.category === "CAFE") {
            conversation += 8;
        }
        if (index === route.steps.length - 1 && step.category === "BAR") {
            stretch += 10;
        }
    });

    return {
        conversation: clamp(conversation),
        energy: clamp(energy),
        stretch: clamp(stretch),
    };
};

const formatMetricLabel = (value: number) => {
    if (value >= 75) return "높음";
    if (value >= 50) return "중간";
    return "낮음";
};

const buildRouteIdentity = (route: KeywordRecommendationRoute) => {
    const categories = route.steps.map((step) => step.category || "ACTIVITY");
    if (categories.includes("BAR")) {
        return "밤까지 이어지는 흐름";
    }
    if (categories.includes("KARAOKE")) {
        return "마지막까지 텐션을 유지하는 흐름";
    }
    if (categories.includes("SHOPPING")) {
        return "구경과 비교가 자연스럽게 섞이는 흐름";
    }
    if (categories[0] === "ACTIVITY" || categories[0] === "PC_ROOM") {
        return "초반에 분위기를 끌어올리는 흐름";
    }
    if (categories[categories.length - 1] === "CAFE") {
        return "대화로 여운을 남기는 흐름";
    }
    return "밸런스를 우선한 기본 흐름";
};

const buildStageLabel = (index: number, total: number) => {
    if (total === 2) {
        return index === 0 ? "START" : "FINISH";
    }

    if (index === 0) return "OPEN";
    if (index === total - 1) return "FINISH";
    if (index === 1) return "PEAK";
    return "FLOW";
};

type SelectedRouteLocationState = {
    keyword?: string;
    meetingType?: string;
    mood?: string;
    location?: string;
    planHint?: string;
    selectedPlaces?: KeywordRecommendation[];
};

export default function SelectedRoutePage() {
    const location = useLocation();
    const navigate = useNavigate();
    const routeState = (location.state || {}) as SelectedRouteLocationState;

    const initialSelectedPlaces = useMemo(
        () =>
            [...(routeState.selectedPlaces || [])].sort(
                (left, right) =>
                    CATEGORY_ORDER.indexOf(left.category || "ACTIVITY")
                    - CATEGORY_ORDER.indexOf(right.category || "ACTIVITY"),
            ),
        [routeState.selectedPlaces],
    );

    const [selectedPlaces, setSelectedPlaces] = useState<KeywordRecommendation[]>(initialSelectedPlaces);
    const [routes, setRoutes] = useState<KeywordRecommendationRoute[]>([]);
    const [source, setSource] = useState("");
    const [warning, setWarning] = useState("");
    const [error, setError] = useState("");
    const [shareMessage, setShareMessage] = useState("");
    const [loading, setLoading] = useState(false);
    const [activeRouteIndex, setActiveRouteIndex] = useState(0);

    const keyword = routeState.keyword || routeState.location || "";
    const meetingType = routeState.meetingType || "";
    const mood = routeState.mood || "";
    const area = routeState.location || "";
    const planHint = routeState.planHint || "";
    const meetingSummary = `${meetingType || "모임"} · ${mood || "분위기"} · ${keyword || area || "지역"}`;

    useEffect(() => {
        if (initialSelectedPlaces.length < 2) {
            setError("이전 페이지에서 최소 2개의 장소를 고른 뒤 다시 들어와 주세요.");
            return;
        }

        let active = true;
        setLoading(true);
        setError("");
        setWarning("");
        setShareMessage("");

        recommendRoutesFromSelections({
            keyword,
            meetingType,
            mood,
            location: area,
            planHint,
            selectedPlaces: initialSelectedPlaces,
        })
            .then((result) => {
                if (!active) {
                    return;
                }

                setSelectedPlaces(result.selectedPlaces || initialSelectedPlaces);
                setRoutes(result.routes || []);
                setSource(result.source || "");
                setWarning(
                    result.warning
                        || (result.source !== "AI" ? "AI 대신 선택한 장소를 바탕으로 기본 플랜을 재구성했습니다." : ""),
                );
                setActiveRouteIndex(0);
            })
            .catch((fetchError) => {
                if (!active) {
                    return;
                }

                if (axios.isAxiosError(fetchError)) {
                    const message = fetchError.response?.data?.message;
                    setError(message || "선택한 장소 기반 플랜을 불러오지 못했습니다.");
                    return;
                }
                setError("선택한 장소 기반 플랜을 불러오지 못했습니다.");
            })
            .finally(() => {
                if (active) {
                    setLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, [area, initialSelectedPlaces, keyword, meetingType, mood, planHint]);

    const activeRoute = routes[activeRouteIndex] || null;
    const activeMetrics = useMemo(
        () => (activeRoute ? deriveRouteMetrics(activeRoute) : null),
        [activeRoute],
    );

    const handleShare = async () => {
        const shareUrl = window.location.href;
        const shareData = {
            title: "Meeting Mate 선택 장소 플랜",
            text: `${meetingSummary} 기준으로 다시 짠 플랜`,
            url: shareUrl,
        };

        try {
            if (navigator.share) {
                await navigator.share(shareData);
                setShareMessage("플랜 공유 창을 열었습니다.");
                return;
            }

            await navigator.clipboard.writeText(shareUrl);
            setShareMessage("플랜 링크를 클립보드에 복사했습니다.");
        } catch {
            setShareMessage("공유를 완료하지 못했습니다. 브라우저 설정을 확인해 주세요.");
        }
    };

    return (
        <div className="min-h-screen bg-stone-50 text-slate-900">
            <Header />

            <main className="mx-auto max-w-7xl px-4 pb-20 pt-10 md:px-6">
                <section className="rounded-[32px] border border-slate-200 bg-white px-6 py-8 shadow-sm md:px-8">
                    <div className="grid gap-8 lg:grid-cols-[1.05fr_0.95fr] lg:items-end">
                        <div>
                            <p className="text-sm uppercase tracking-[0.28em] text-slate-500">Plan Studio</p>
                            <h2 className="mt-3 break-keep text-3xl font-semibold leading-tight text-slate-900 md:text-4xl">
                                고른 장소로 플랜을 다시 설계
                            </h2>
                            <p className="mt-4 max-w-2xl text-sm leading-6 text-slate-600 md:text-base">
                                이번 화면은 단순 순서 나열이 아니라, 어떤 흐름으로 놀지 비교하고 하나를 골라 구체적으로 보는 플랜 보드입니다.
                            </p>
                            {planHint && (
                                <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-600">
                                    원하는 흐름: {planHint}
                                </p>
                            )}
                            <div className="mt-5 flex flex-wrap gap-2">
                                <span className="rounded-full bg-slate-900 px-3 py-1 text-xs font-semibold text-white">
                                    {meetingType || "모임"}
                                </span>
                                <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                                    {mood || "분위기"}
                                </span>
                                <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-700">
                                    {keyword || area || "지역"}
                                </span>
                            </div>
                        </div>

                        <div className="grid gap-3 md:grid-cols-3">
                            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                                <p className="text-xs uppercase tracking-[0.2em] text-slate-400">선택 장소</p>
                                <p className="mt-2 text-sm font-medium text-slate-900">{selectedPlaces.length}곳</p>
                            </div>
                            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                                <p className="text-xs uppercase tracking-[0.2em] text-slate-400">추천 소스</p>
                                <p className="mt-2 text-sm font-medium text-slate-900">{formatSource(source)}</p>
                            </div>
                            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                                <p className="text-xs uppercase tracking-[0.2em] text-slate-400">모임 정보</p>
                                <p className="mt-2 text-sm font-medium text-slate-900">{meetingSummary}</p>
                            </div>
                        </div>
                    </div>
                </section>

                {warning && (
                    <p className="mt-6 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
                        {warning}
                    </p>
                )}
                {error && (
                    <p className="mt-6 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                        {error}
                    </p>
                )}
                {shareMessage && (
                    <p className="mt-6 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
                        {shareMessage}
                    </p>
                )}

                <section className="mt-8 rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                    <div className="mb-5 flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                        <div>
                            <p className="text-sm font-medium text-slate-500">선택한 카드</p>
                            <h3 className="mt-1 text-2xl font-semibold text-slate-900">
                                이번 플랜에 들어가는 장소들
                            </h3>
                        </div>
                        <p className="text-sm leading-6 text-slate-500">
                            이 장소들만 사용해서 아래 플랜들이 만들어집니다.
                        </p>
                    </div>

                    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                        {selectedPlaces.map((place) => (
                            <article
                                key={`${place.category}-${place.name}-${place.address}`}
                                className={`rounded-[24px] border p-5 ${CATEGORY_STYLE[place.category || "ACTIVITY"] || "border-slate-200 bg-white"}`}
                            >
                                <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${CATEGORY_BADGE[place.category || "ACTIVITY"] || "bg-slate-200 text-slate-700"}`}>
                                    {formatCategoryTitle(place.category)}
                                </span>
                                <h4 className="mt-3 break-keep text-xl font-semibold leading-tight text-slate-900">
                                    {place.name}
                                </h4>
                                <p className="mt-2 text-sm leading-6 text-slate-500">{place.address}</p>
                                {place.reason && (
                                    <p className="mt-3 text-sm leading-6 text-slate-600">{place.reason}</p>
                                )}
                            </article>
                        ))}
                    </div>
                </section>

                <section className="mt-8">
                    <div className="mb-5 flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                        <div>
                            <p className="text-sm font-medium text-slate-500">플랜 옵션</p>
                            <h3 className="mt-1 text-2xl font-semibold text-slate-900">
                                서로 다른 흐름으로 비교해서 고르기
                            </h3>
                        </div>
                        <p className="text-sm leading-6 text-slate-500">
                            한 플랜을 누르면 아래 보드에서 순서와 성격을 자세히 볼 수 있습니다.
                        </p>
                    </div>

                    {loading && (
                        <div className="grid gap-5 lg:grid-cols-3">
                            {[0, 1, 2].map((index) => (
                                <div key={index} className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                                    <div className="h-4 w-20 rounded-full bg-slate-200" />
                                    <div className="mt-4 h-10 w-48 rounded-full bg-slate-200" />
                                    <div className="mt-5 h-24 rounded-3xl bg-slate-100" />
                                </div>
                            ))}
                        </div>
                    )}

                    {!loading && routes.length > 0 && (
                        <div className="grid gap-5 lg:grid-cols-3">
                            {routes.map((route, index) => {
                                const routeMetrics = deriveRouteMetrics(route);
                                const selected = index === activeRouteIndex;

                                return (
                                    <button
                                        key={`${route.title}-${index}`}
                                        type="button"
                                        onClick={() => setActiveRouteIndex(index)}
                                        className={`rounded-[28px] border p-6 text-left shadow-sm transition ${
                                            selected
                                                ? "border-slate-900 bg-slate-900 text-white"
                                                : "border-slate-200 bg-white hover:border-slate-300"
                                        }`}
                                    >
                                        <p className={`text-xs uppercase tracking-[0.24em] ${selected ? "text-slate-300" : "text-slate-400"}`}>
                                            Plan {index + 1}
                                        </p>
                                        <h4 className={`mt-3 break-keep text-2xl font-semibold leading-tight ${selected ? "text-white" : "text-slate-900"}`}>
                                            {route.title}
                                        </h4>
                                        {route.summary && (
                                            <p className={`mt-3 text-sm leading-6 ${selected ? "text-slate-200" : "text-slate-600"}`}>
                                                {route.summary}
                                            </p>
                                        )}

                                        <div className="mt-5 flex flex-wrap gap-2">
                                            {route.steps.map((step) => (
                                                <span
                                                    key={`${route.title}-${step.order}-${step.placeName}`}
                                                    className={`rounded-full px-3 py-1 text-xs font-semibold ${
                                                        selected
                                                            ? "bg-white/10 text-white"
                                                            : "bg-slate-100 text-slate-700"
                                                    }`}
                                                >
                                                    {step.placeName}
                                                </span>
                                            ))}
                                        </div>

                                        <div className="mt-6 grid gap-3 sm:grid-cols-3">
                                            <div>
                                                <p className={`text-[11px] uppercase tracking-[0.2em] ${selected ? "text-slate-300" : "text-slate-400"}`}>
                                                    대화
                                                </p>
                                                <p className={`mt-1 text-sm font-semibold ${selected ? "text-white" : "text-slate-900"}`}>
                                                    {formatMetricLabel(routeMetrics.conversation)}
                                                </p>
                                            </div>
                                            <div>
                                                <p className={`text-[11px] uppercase tracking-[0.2em] ${selected ? "text-slate-300" : "text-slate-400"}`}>
                                                    텐션
                                                </p>
                                                <p className={`mt-1 text-sm font-semibold ${selected ? "text-white" : "text-slate-900"}`}>
                                                    {formatMetricLabel(routeMetrics.energy)}
                                                </p>
                                            </div>
                                            <div>
                                                <p className={`text-[11px] uppercase tracking-[0.2em] ${selected ? "text-slate-300" : "text-slate-400"}`}>
                                                    길이
                                                </p>
                                                <p className={`mt-1 text-sm font-semibold ${selected ? "text-white" : "text-slate-900"}`}>
                                                    {formatMetricLabel(routeMetrics.stretch)}
                                                </p>
                                            </div>
                                        </div>
                                    </button>
                                );
                            })}
                        </div>
                    )}

                    {!loading && !routes.length && !error && (
                        <div className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm">
                            <p className="text-sm text-slate-500">
                                선택한 장소로 플랜을 아직 만들지 못했습니다. 이전 페이지에서 장소를 다시 골라보세요.
                            </p>
                        </div>
                    )}
                </section>

                {!loading && activeRoute && (
                    <section className="mt-8 grid gap-6 lg:grid-cols-[1.04fr_0.96fr]">
                        <article className="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm md:p-8">
                            <p className="text-sm uppercase tracking-[0.24em] text-slate-500">Focused Plan</p>
                            <h3 className="mt-3 break-keep text-3xl font-semibold leading-tight text-slate-900">
                                {activeRoute.title}
                            </h3>
                            {activeRoute.summary && (
                                <p className="mt-4 max-w-2xl text-sm leading-6 text-slate-600">
                                    {activeRoute.summary}
                                </p>
                            )}
                            {activeRoute.fitReason && (
                                <p className="mt-4 inline-flex rounded-full bg-emerald-700 px-4 py-2 text-sm font-medium text-white">
                                    {activeRoute.fitReason}
                                </p>
                            )}

                            <ol className="mt-8 space-y-5">
                                {activeRoute.steps.map((step, index) => (
                                    <li
                                        key={`${activeRoute.title}-${step.order}-${step.placeName}`}
                                        className="relative rounded-[28px] border border-slate-200 bg-slate-50 p-5"
                                    >
                                        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                                            <div className="max-w-2xl">
                                                <p className="text-xs uppercase tracking-[0.24em] text-slate-400">
                                                    {buildStageLabel(index, activeRoute.steps.length)}
                                                </p>
                                                <div className="mt-3 flex flex-wrap items-center gap-3">
                                                    <span className="inline-flex h-10 w-10 items-center justify-center rounded-full bg-slate-900 text-sm font-semibold text-white">
                                                        {step.order}
                                                    </span>
                                                    <span className={`rounded-full px-3 py-1 text-xs font-semibold ${CATEGORY_BADGE[step.category || "ACTIVITY"] || "bg-slate-200 text-slate-700"}`}>
                                                        {formatCategoryTitle(step.category)}
                                                    </span>
                                                </div>
                                                <h4 className="mt-4 break-keep text-2xl font-semibold text-slate-900">
                                                    {step.placeName}
                                                </h4>
                                                <p className="mt-2 text-sm leading-6 text-slate-500">{step.address}</p>
                                                {step.reason && (
                                                    <p className="mt-4 text-sm leading-6 text-slate-600">
                                                        {step.reason}
                                                    </p>
                                                )}
                                            </div>
                                            {index < activeRoute.steps.length - 1 && (
                                                <div className="hidden h-full items-center md:flex">
                                                    <div className="rounded-full border border-slate-200 bg-white px-4 py-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
                                                        Next
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    </li>
                                ))}
                            </ol>
                        </article>

                        <aside className="space-y-6 lg:sticky lg:top-6 lg:self-start">
                            <section className="rounded-[32px] border border-slate-200 bg-slate-900 p-6 text-white shadow-sm">
                                <p className="text-sm uppercase tracking-[0.24em] text-slate-300">Plan Identity</p>
                                <h3 className="mt-3 text-3xl font-semibold leading-tight">
                                    {buildRouteIdentity(activeRoute)}
                                </h3>
                                <p className="mt-4 text-sm leading-6 text-slate-200">
                                    지금 선택된 플랜이 어떤 식으로 분위기를 만들고 마무리하는지 한눈에 비교할 수 있도록 요약했습니다.
                                </p>

                                {activeMetrics && (
                                    <div className="mt-6 space-y-4">
                                        {[
                                            ["대화 밀도", activeMetrics.conversation],
                                            ["활동 텐션", activeMetrics.energy],
                                            ["밤까지 이어짐", activeMetrics.stretch],
                                        ].map(([label, value]) => (
                                            <div key={label}>
                                                <div className="mb-2 flex items-center justify-between text-sm">
                                                    <span>{label}</span>
                                                    <span>{value}%</span>
                                                </div>
                                                <div className="h-2 rounded-full bg-white/10">
                                                    <div
                                                        className="h-2 rounded-full bg-emerald-400"
                                                        style={{ width: `${value}%` }}
                                                    />
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </section>

                            <section className="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                                <p className="text-sm font-medium text-slate-500">플랜에 쓰인 장소</p>
                                <h3 className="mt-2 text-2xl font-semibold text-slate-900">
                                    지금 조합되는 카드들
                                </h3>
                                <div className="mt-5 flex flex-wrap gap-2">
                                    {activeRoute.steps.map((step) => (
                                        <span
                                            key={`${activeRoute.title}-${step.order}-${step.placeName}-chip`}
                                            className={`rounded-full px-3 py-2 text-xs font-semibold ${CATEGORY_BADGE[step.category || "ACTIVITY"] || "bg-slate-200 text-slate-700"}`}
                                        >
                                            {step.placeName}
                                        </span>
                                    ))}
                                </div>
                                <div className="mt-6 flex flex-wrap gap-3">
                                    <Button variant="secondary" onClick={() => navigate(-1)}>
                                        장소 다시 고르기
                                    </Button>
                                    <Button onClick={handleShare}>공유하기</Button>
                                </div>
                            </section>
                        </aside>
                    </section>
                )}
            </main>
        </div>
    );
}
