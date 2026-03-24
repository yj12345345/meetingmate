import { useState } from "react";
import { useNavigate } from "react-router-dom";
import Button from "../components/Button";
import Header from "../components/Header";

const REQUEST_EXAMPLES = [
    "친구랑 쇼핑할 건데 성수 근처에서 둘러볼 편집샵이랑 중간에 쉬기 좋은 카페를 같이 보고 싶어.",
    "잠실에서 연인이랑 분위기 좋은 양식집, 케이크 강한 카페, 저녁 먹고 산책할 곳까지 보고 싶어.",
    "신촌에서 친구들이랑 PC방 갔다가 삼겹살 먹고 조용한 술집 들렀다가 코인노래방 가는 흐름으로 찾고 싶어.",
    "청주 여행 중이라 관광 동선 안에서 실패 확률 낮은 맛집이랑 카페 위주로 추천받고 싶어.",
];

const MEETING_PRESETS = ["친구 모임", "연인 데이트", "직장 동료 모임", "가족 나들이", "혼자 여행"];
const MOOD_PRESETS = ["조용한", "활기찬", "감성적인", "가성비 좋은", "여유로운", "로컬 느낌"];

const appendLine = (current: string, next: string) =>
    current.trim() ? `${current.trim()}\n${next}` : next;

export default function SelectPage() {
    const navigate = useNavigate();

    const [meetingType, setMeetingType] = useState("");
    const [location, setLocation] = useState("");
    const [mood, setMood] = useState("");
    const [mainRequest, setMainRequest] = useState("");
    const [extraRequest, setExtraRequest] = useState("");
    const [avoidKeywords, setAvoidKeywords] = useState("");

    const handleSubmit = () => {
        const planSegments = [
            mainRequest.trim() ? `메인 요청: ${mainRequest.trim()}` : "",
            extraRequest.trim() ? `추가 요청: ${extraRequest.trim()}` : "",
            avoidKeywords.trim() ? `피하고 싶은 것: ${avoidKeywords.trim()}` : "",
        ].filter(Boolean);
        const planHint = planSegments.join("\n");

        const keyword = (location || mainRequest || extraRequest || meetingType || mood).trim();
        if (!keyword) {
            alert("지역이나 원하는 일정 설명을 적어주세요.");
            return;
        }

        const searchParams = new URLSearchParams();
        searchParams.set("keyword", keyword);
        if (meetingType.trim()) searchParams.set("meetingType", meetingType.trim());
        if (mood.trim()) searchParams.set("mood", mood.trim());
        if (location.trim()) searchParams.set("location", location.trim());
        if (planHint.trim()) searchParams.set("planHint", planHint.trim());

        navigate(`/recommend?${searchParams.toString()}`, {
            state: { meetingType, mood, location, planHint },
        });
    };

    return (
        <div className="min-h-screen bg-stone-50 text-slate-900">
            <Header />

            <main className="mx-auto max-w-6xl px-4 pb-20 pt-10 md:px-6">
                <section className="grid gap-6 xl:grid-cols-[1.08fr_0.92fr]">
                    <article className="rounded-[32px] border border-slate-200 bg-white px-6 py-8 shadow-sm md:px-8">
                        <p className="text-sm uppercase tracking-[0.28em] text-slate-500">Flexible Input</p>
                        <h2 className="mt-3 break-keep text-3xl font-semibold leading-tight md:text-4xl">
                            일정이나 모임을 자유롭게 적어주세요
                        </h2>
                        <p className="mt-4 max-w-3xl text-sm leading-6 text-slate-600 md:text-base">
                            정해진 동선에 맞추기보다, 원하는 지역과 분위기 그리고 하고 싶은 일을 자연스럽게 적는 방식으로 바꿨습니다.
                            쇼핑, 여행, 맛집 탐방, 카페 투어처럼 카테고리가 달라도 그대로 입력하면 됩니다.
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
                                    placeholder="예) 친구랑 쇼핑, 연인 데이트, 청주 여행, 직장 동료 저녁 모임"
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
                                    어디를 중심으로 볼까요?
                                </label>
                                <input
                                    type="text"
                                    value={location}
                                    onChange={(event) => setLocation(event.target.value)}
                                    placeholder="예) 신촌, 잠실, 성수, 청주 성안길, 강릉 안목"
                                    className="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                                />
                            </div>

                            <div className="md:col-span-2">
                                <label className="mb-2 block text-sm font-semibold text-slate-800">
                                    원하는 내용은 자유롭게 적어주세요
                                </label>
                                <textarea
                                    value={mainRequest}
                                    onChange={(event) => setMainRequest(event.target.value)}
                                    rows={7}
                                    placeholder="예) 친구랑 만나서 근처 쇼핑 좀 하다가 삼겹살 맛집 가고, 너무 시끄럽지 않은 술집 들렀다가 코인노래방으로 마무리하고 싶어. 또는 청주 여행 중이라 성안길 근처 맛집과 카페 위주로 보고 싶어."
                                    className="w-full resize-none rounded-[28px] border border-slate-200 px-4 py-4 text-sm leading-6 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                                />
                                <p className="mt-2 text-sm text-slate-500">
                                    메뉴, 카테고리, 동선, 분위기, 동행자, 여행 여부를 섞어서 한 문장으로 적어도 됩니다.
                                </p>
                            </div>

                            <div>
                                <label className="mb-2 block text-sm font-semibold text-slate-800">
                                    분위기나 톤이 있나요?
                                </label>
                                <input
                                    type="text"
                                    value={mood}
                                    onChange={(event) => setMood(event.target.value)}
                                    placeholder="예) 조용한, 로컬 느낌, 감성적인, 가성비 좋은, 활기찬"
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

                            <div>
                                <label className="mb-2 block text-sm font-semibold text-slate-800">
                                    추가로 찾고 싶은 것이 있나요?
                                </label>
                                <input
                                    type="text"
                                    value={extraRequest}
                                    onChange={(event) => setExtraRequest(event.target.value)}
                                    placeholder="예) 쇼핑 + 카페만, 맛집 위주, 야경도 같이, 조용한 술집까지"
                                    className="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                                />
                            </div>

                            <div className="md:col-span-2">
                                <label className="mb-2 block text-sm font-semibold text-slate-800">
                                    피하고 싶은 것이 있나요?
                                </label>
                                <input
                                    type="text"
                                    value={avoidKeywords}
                                    onChange={(event) => setAvoidKeywords(event.target.value)}
                                    placeholder="예) 프랜차이즈 제외, 너무 시끄러운 술집 말고, 웨이팅 심한 곳 제외"
                                    className="w-full rounded-2xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                                />
                            </div>
                        </div>

                        <div className="mt-8 flex flex-wrap gap-3">
                            <Button onClick={handleSubmit}>실제 장소 추천 보기</Button>
                        </div>
                    </article>

                    <aside className="space-y-6">
                        <section className="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm">
                            <p className="text-sm uppercase tracking-[0.24em] text-slate-500">How To Ask</p>
                            <h3 className="mt-3 text-2xl font-semibold text-slate-900">
                                이런 식으로 적으면 더 잘 맞습니다
                            </h3>
                            <div className="mt-5 space-y-3">
                                {REQUEST_EXAMPLES.map((example) => (
                                    <button
                                        key={example}
                                        type="button"
                                        onClick={() => setMainRequest((current) => appendLine(current, example))}
                                        className="w-full rounded-3xl border border-slate-200 bg-slate-50 px-4 py-4 text-left text-sm leading-6 text-slate-700 transition hover:border-slate-300 hover:bg-white"
                                    >
                                        {example}
                                    </button>
                                ))}
                            </div>
                        </section>

                        <section className="rounded-[32px] border border-slate-200 bg-[#faf6ef] p-6 shadow-sm">
                            <p className="text-sm uppercase tracking-[0.24em] text-slate-500">What Changes</p>
                            <ul className="mt-4 space-y-3 text-sm leading-6 text-slate-700">
                                <li>카테고리를 미리 고르지 않아도, 입력 내용에 따라 쇼핑/식당/카페/술집/놀거리처럼 유동적으로 만들어집니다.</li>
                                <li>여행, 데이트, 친구 모임처럼 넓은 요청도 지역 기준으로 실제 장소를 다시 검증합니다.</li>
                                <li>한 카테고리에서 더 많은 후보를 비교할 수 있도록 결과 화면도 촘촘하게 정리됩니다.</li>
                            </ul>
                        </section>
                    </aside>
                </section>
            </main>
        </div>
    );
}
