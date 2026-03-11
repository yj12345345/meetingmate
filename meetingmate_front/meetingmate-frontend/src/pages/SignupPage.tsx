import { useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import Header from "../components/Header";
import Button from "../components/Button";
import { signupLocal } from "../api/auth";

export default function SignupPage() {
    const navigate = useNavigate();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [nickname, setNickname] = useState("");
    const [loading, setLoading] = useState(false);

    const handleSignup = async () => {
        if (!email.trim() || !password.trim() || !nickname.trim()) {
            alert("이메일, 비밀번호, 닉네임을 모두 입력하세요.");
            return;
        }

        setLoading(true);
        try {
            await signupLocal({ email, password, nickname });
            alert("회원가입이 완료되었습니다. 로그인해주세요.");
            navigate("/login");
        } catch (error) {
            if (axios.isAxiosError(error)) {
                const message = error.response?.data?.message;
                alert(message || "회원가입 실패");
                return;
            }
            alert("회원가입 실패");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen">
            <Header />

            <main className="max-w-sm mx-auto mt-24 px-4">
                <h2 className="text-2xl font-bold mb-8 text-center">회원가입</h2>

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
                    <input
                        type="text"
                        placeholder="닉네임"
                        value={nickname}
                        onChange={(e) => setNickname(e.target.value)}
                        className="w-full px-4 py-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-black"
                    />
                </div>

                <div className="mt-6">
                    <Button variant="primary" onClick={handleSignup} className="w-full">
                        {loading ? "가입 중..." : "가입하기"}
                    </Button>
                </div>
            </main>
        </div>
    );
}
