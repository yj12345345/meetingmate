import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
    clearPostLoginRedirect,
    getPostLoginRedirect,
} from "../utils/auth";

export default function OAuthCallbackPage() {
    const navigate = useNavigate();

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const tokenFromQuery = params.get("token");
        const tokenInStorage = localStorage.getItem("accessToken");
        const redirectTarget = getPostLoginRedirect(params.get("redirect"));

        if (tokenFromQuery) {
            localStorage.setItem("accessToken", tokenFromQuery);
            clearPostLoginRedirect();
            navigate(redirectTarget, { replace: true });
            return;
        }

        // 이미 토큰이 있으면 통과 (재로그인/새로고침 대비)
        if (tokenInStorage) {
            clearPostLoginRedirect();
            navigate(redirectTarget, { replace: true });
            return;
        }

        alert("로그인 실패");
        navigate("/login");
    }, [navigate]);

    return <div style={{ padding: 24 }}>로그인 처리 중...</div>;
}
