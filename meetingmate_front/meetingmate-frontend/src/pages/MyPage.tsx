import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Header from "../components/Header";
import Button from "../components/Button";
import { deleteMyAccount, getMyInfo, updateMyProfile } from "../api/user";
import type { UserMe } from "../types/user";

export default function MyPage() {
    const navigate = useNavigate();
    const [me, setMe] = useState<UserMe | null>(null);
    const [nickname, setNickname] = useState("");
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const load = async () => {
            try {
                const data = await getMyInfo();
                setMe(data);
                setNickname(data.nickname);
            } catch {
                alert("사용자 정보를 불러오지 못했습니다. 다시 로그인해주세요.");
                localStorage.removeItem("accessToken");
                navigate("/login");
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [navigate]);

    const handleUpdateNickname = async () => {
        if (!nickname.trim()) {
            alert("닉네임을 입력하세요.");
            return;
        }
        try {
            const updated = await updateMyProfile({ nickname: nickname.trim() });
            setMe(updated);
            setNickname(updated.nickname);
            alert("닉네임이 수정되었습니다.");
        } catch {
            alert("닉네임 수정에 실패했습니다.");
        }
    };

    const handleDelete = async () => {
        const ok = window.confirm("정말 탈퇴하시겠습니까? 이 작업은 되돌릴 수 없습니다.");
        if (!ok) return;

        try {
            await deleteMyAccount();
            localStorage.removeItem("accessToken");
            alert("회원 탈퇴가 완료되었습니다.");
            navigate("/home");
        } catch {
            alert("회원 탈퇴에 실패했습니다.");
        }
    };

    if (loading) {
        return (
            <div className="min-h-screen">
                <Header />
                <main className="max-w-xl mx-auto mt-24 px-4 text-gray-500">로딩 중...</main>
            </div>
        );
    }

    if (!me) return null;

    return (
        <div className="min-h-screen">
            <Header />

            <main className="max-w-xl mx-auto mt-24 px-4">
                <h2 className="text-2xl font-bold mb-8">마이페이지</h2>

                <div className="space-y-4 border rounded-xl p-5 mb-8">
                    <div>
                        <p className="text-sm text-gray-500">이메일</p>
                        <p className="font-medium">{me.email || "-"}</p>
                    </div>
                    <div>
                        <p className="text-sm text-gray-500">로그인 제공자</p>
                        <p className="font-medium">{me.provider}</p>
                    </div>
                    <div>
                        <p className="text-sm text-gray-500">가입일</p>
                        <p className="font-medium">{new Date(me.createdAt).toLocaleString()}</p>
                    </div>
                </div>

                <div className="border rounded-xl p-5 mb-6">
                    <p className="font-semibold mb-3">닉네임 수정</p>
                    <div className="flex gap-2">
                        <input
                            type="text"
                            value={nickname}
                            onChange={(e) => setNickname(e.target.value)}
                            className="flex-1 px-4 py-3 border rounded-lg focus:outline-none focus:ring-2 focus:ring-black"
                        />
                        <Button variant="primary" onClick={handleUpdateNickname}>
                            저장
                        </Button>
                    </div>
                </div>

                <div className="border rounded-xl p-5">
                    <p className="font-semibold mb-3 text-red-600">회원 탈퇴</p>
                    <p className="text-sm text-gray-500 mb-4">
                        계정을 삭제하면 관련 데이터가 모두 삭제됩니다.
                    </p>
                    <button
                        type="button"
                        onClick={handleDelete}
                        className="w-full px-5 py-3 rounded-xl font-semibold text-white bg-red-600 hover:bg-red-700 transition-all"
                    >
                        회원 탈퇴하기
                    </button>
                </div>
            </main>
        </div>
    );
}
