import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import axios from "axios";
import Header from "../components/Header";
import Button from "../components/Button";
import { loginLocal } from "../api/auth";
import {
    clearPostLoginRedirect,
    getPostLoginRedirect,
    savePostLoginRedirect,
} from "../utils/auth";

export default function LoginPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const enableGoogleLogin = import.meta.env.VITE_ENABLE_GOOGLE_LOGIN === "true";
    const redirectParam = new URLSearchParams(location.search).get("redirect");
    const redirectTarget = getPostLoginRedirect(redirectParam);
    const loginMessage =
        typeof location.state === "object" &&
        location.state !== null &&
        "message" in location.state &&
        typeof location.state.message === "string"
            ? location.state.message
            : "";

    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [loading, setLoading] = useState(false);

    // 이메일 로그인
    const handleEmailLogin = async () => {
        if (!email.trim() || !password.trim()) {
            alert("이메일과 비밀번호를 입력하세요.");
            return;
        }
        setLoading(true);
        try {
            const accessToken = await loginLocal({ email, password });
            localStorage.setItem("accessToken", accessToken);
            clearPostLoginRedirect();
            navigate(redirectTarget, { replace: true });
        } catch (error) {
            if (axios.isAxiosError(error)) {
                const message = error.response?.data?.message;
                alert(message || "로그인 실패");
                return;
            }
            alert("로그인 실패");
        } finally {
            setLoading(false);
        }
    };

    // 카카오 로그인
    const handleKakaoLogin = () => {
        savePostLoginRedirect(redirectTarget);
        window.location.href =
            "https://meetingmate.duckdns.org/oauth2/authorization/kakao";
    };

    // 구글 로그인
    const handleGoogleLogin = () => {
        savePostLoginRedirect(redirectTarget);
        window.location.href =
            "https://meetingmate.duckdns.org/oauth2/authorization/google";
    };

    return (
        <div className="min-h-screen">
            <Header />

            <main className="max-w-sm mx-auto mt-24 px-4">
                <h2 className="text-2xl font-bold mb-8 text-center">
                    로그인
                </h2>

                {loginMessage && (
                    <p className="mb-6 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-700">
                        {loginMessage}
                    </p>
                )}

                {/* 이메일 입력 */}
                <div className="flex flex-col gap-4">
                    <input
                        type="email"
                        placeholder="이메일"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        className="w-full px-4 py-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-black"
                    />

                    <input
                        type="password"
                        placeholder="비밀번호"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        className="w-full px-4 py-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-black"
                    />
                </div>

                {/* 이메일 로그인 버튼 */}
                <div className="mt-6">
                    <Button
                        variant="primary"
                        onClick={handleEmailLogin}
                        className="w-full"
                    >
                        {loading ? "로그인 중..." : "이메일로 로그인"}
                    </Button>
                </div>

                {/* 구분선 */}
                <div className="flex items-center my-10">
                    <div className="flex-1 h-px bg-gray-200" />
                    <span className="px-4 text-sm text-gray-400">
            또는
          </span>
                    <div className="flex-1 h-px bg-gray-200" />
                </div>

                {/* 카카오 로그인 */}
                <Button
                    variant="secondary"
                    onClick={handleKakaoLogin}
                    className="w-full"
                >
                    카카오로 로그인
                </Button>

                {enableGoogleLogin && (
                    <div className="mt-3">
                        <Button
                            variant="secondary"
                            onClick={handleGoogleLogin}
                            className="w-full"
                        >
                            구글로 로그인
                        </Button>
                    </div>
                )}
            </main>
        </div>
    );
}
