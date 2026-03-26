import { useState } from "react";
import { useNavigate } from "react-router-dom";
import Button from "../components/Button";
import Header from "../components/Header";

const MEETING_PRESETS = ["친구 모임", "연인 데이트", "직장 동료 모임", "가족 나들이", "혼자 여행"];
const MOOD_PRESETS = ["조용한", "활기찬", "감성적인", "가성비 좋은", "여유로운", "로컬 느낌"];

const normalizeLine = (value: string) => value.replace(/\s+/g, " ").trim();
const AREA_INLINE_PATTERN = /([가-힣A-Za-z0-9]{2,12})\s*(?:근처에서|근처|주변에서|주변|인근에서|인근|일대에서|일대|부근에서|부근|쪽에서|쪽|에서)/g;
const AREA_SUFFIX_PATTERN = /^(?:[가-힣A-Za-z0-9]+)(역|동|구|시|군|읍|면|리)$/;
const AREA_PREFIX_TRAVEL_PATTERN = /([가-힣A-Za-z0-9]{2,12})\s*(?:여행|데이트|나들이|모임)/g;
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

const extractAreaToken = (value: string) => {
    const normalized = normalizeLine(value).replace(/[^\w\u3131-\u318E\uAC00-\uD7A3\s]/g, " ");
    if (!normalized) return "";

    const inlineMatches = normalized.matchAll(AREA_INLINE_PATTERN);
    for (const match of inlineMatches) {
        const candidate = normalizeLine(match[1] || "");
        if (candidate.length >= 2 && !AREA_STOPWORDS.has(candidate)) {
            return candidate.slice(0, 20);
        }
    }

    const travelMatches = normalized.matchAll(AREA_PREFIX_TRAVEL_PATTERN);
    for (const match of travelMatches) {
        const candidate = normalizeLine(match[1] || "");
        if (candidate.length >= 2 && !AREA_STOPWORDS.has(candidate)) {
            return candidate.slice(0, 20);
        }
    }

    const tokens = normalized.split(" ").filter(Boolean);
    const bySuffix = tokens.find((token) => AREA_SUFFIX_PATTERN.test(token) && !AREA_STOPWORDS.has(token));
    if (bySuffix) {
        return bySuffix.slice(0, 20);
    }

    const firstLocationLikeToken = tokens.find((token) => {
        if (token.length < 2) return false;
        if (AREA_STOPWORDS.has(token)) return false;
        if (/[0-9]/.test(token)) return false;
        return true;
    });

    return firstLocationLikeToken ? firstLocationLikeToken.slice(0, 20) : "";
};

export default function SelectPage() {
    const navigate = useNavigate();

    const [meetingType, setMeetingType] = useState("");
    const [location, setLocation] = useState("");
    const [mood, setMood] = useState("");
    const [mainRequest, setMainRequest] = useState("");

    const handleSubmit = () => {
        const normalizedLocation = normalizeLine(location);
        const normalizedRequest = normalizeLine(mainRequest);
        const extractedLocation = extractAreaToken(normalizedLocation) || extractAreaToken(normalizedRequest);
        const finalLocation = extractedLocation || normalizedLocation;

        if (!normalizedLocation) {
            alert("중심 지역을 먼저 입력해 주세요.");
            return;
        }

        if (!normalizedRequest) {
            alert("원하는 내용을 한 줄 이상 입력해 주세요.");
            return;
        }

        const planSegments = [
            `메인 요청: ${normalizedRequest}`,
            mood.trim() ? `분위기: ${normalizeLine(mood)}` : "",
        ].filter(Boolean);

        const planHint = planSegments.join("\n");

        const searchParams = new URLSearchParams();
        searchParams.set("keyword", normalizedRequest);
        searchParams.set("location", finalLocation);
        if (meetingType.trim()) searchParams.set("meetingType", normalizeLine(meetingType));
        if (mood.trim()) searchParams.set("mood", normalizeLine(mood));
        if (planHint.trim()) searchParams.set("planHint", planHint.trim());

        navigate(`/recommend?${searchParams.toString()}`, {
            state: {
                meetingType: normalizeLine(meetingType),
                mood: normalizeLine(mood),
                location: finalLocation,
                planHint,
            },
        });
    };

    return (
        <div className="min-h-screen bg-stone-50 text-slate-900">
            <Header />

            <main className="mx-auto max-w-5xl px-4 pb-20 pt-10 md:px-6">
                <section className="rounded-[32px] border border-slate-200 bg-white px-6 py-8 shadow-sm md:px-8">
                    <p className="text-sm uppercase tracking-[0.28em] text-slate-500">검색</p>
                    <h2 className="mt-3 break-keep text-3xl font-semibold leading-tight md:text-4xl">
                        지역과 요청을 입력해 주세요
                    </h2>
                    <p className="mt-4 text-sm leading-6 text-slate-600 md:text-base">
                        중심 지역과 요청이 비어 있으면 검색이 진행되지 않습니다.
                    </p>

                    <div className="mt-8 grid gap-5 md:grid-cols-2">
                        <div>
                            <label className="mb-2 block text-sm font-semibold text-slate-800">
                                어떤 일정이나 모임인가요?
                            </label>
                            <input
                                type="text"
                                value={meetingType}
                                onChange={(event) => setMeetingType(event.target.value)}
                                placeholder="예) 친구 모임, 연인 데이트"
                                className="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                            />
                            <div className="mt-3 flex flex-wrap gap-2">
                                {MEETING_PRESETS.map((preset) => (
                                    <button
                                        key={preset}
                                        type="button"
                                        onClick={() => setMeetingType(preset)}
                                        className="rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-200"
                                    >
                                        {preset}
                                    </button>
                                ))}
                            </div>
                        </div>

                        <div>
                            <label className="mb-2 block text-sm font-semibold text-slate-800">
                                어디를 중심으로 볼까요? <span className="text-rose-500">*</span>
                            </label>
                            <input
                                type="text"
                                value={location}
                                onChange={(event) => setLocation(event.target.value)}
                                placeholder="예) 신촌, 성수, 잠실, 청주"
                                className="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                            />
                        </div>

                        <div className="md:col-span-2">
                            <label className="mb-2 block text-sm font-semibold text-slate-800">
                                원하는 내용 <span className="text-rose-500">*</span>
                            </label>
                            <textarea
                                value={mainRequest}
                                onChange={(event) => setMainRequest(event.target.value)}
                                rows={6}
                                placeholder="예) 친구들이랑 PC방 갔다가 삼겹살 먹고, 조용한 술집 들렀다가 코인노래방 가고 싶어."
                                className="w-full resize-none rounded-[28px] border border-slate-200 px-4 py-4 text-sm leading-6 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                            />
                        </div>

                        <div className="md:col-span-2">
                            <label className="mb-2 block text-sm font-semibold text-slate-800">
                                분위기(선택)
                            </label>
                            <input
                                type="text"
                                value={mood}
                                onChange={(event) => setMood(event.target.value)}
                                placeholder="예) 조용한, 감성적인, 가성비 좋은"
                                className="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                            />
                            <div className="mt-3 flex flex-wrap gap-2">
                                {MOOD_PRESETS.map((preset) => (
                                    <button
                                        key={preset}
                                        type="button"
                                        onClick={() => setMood(preset)}
                                        className="rounded-full bg-amber-50 px-3 py-1.5 text-xs font-semibold text-amber-700 transition hover:bg-amber-100"
                                    >
                                        {preset}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>

                    <div className="mt-8 flex flex-wrap gap-3">
                        <Button onClick={handleSubmit}>실제 장소 추천 보기</Button>
                    </div>
                </section>
            </main>
        </div>
    );
}
