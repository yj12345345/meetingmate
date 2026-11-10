import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { refreshAccessToken } from "../api/http";
import {
    clearPostLoginRedirect,
    getPostLoginRedirect,
} from "../utils/auth";

/**
 * Landing page after Kakao/Google login. The backend no longer puts the access token in
 * this URL (it leaked into browser history and server logs); it sets an HttpOnly
 * refresh-token cookie instead, which we exchange for an access token here.
 */
export default function OAuthCallbackPage() {
    const navigate = useNavigate();

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const redirectTarget = getPostLoginRedirect(params.get("redirect"));

        refreshAccessToken()
            .then(() => {
                clearPostLoginRedirect();
                navigate(redirectTarget, { replace: true });
            })
            .catch(() => {
                alert("로그인 실패");
                navigate("/login", { replace: true });
            });
    }, [navigate]);

    return <div style={{ padding: 24 }}>로그인 처리 중...</div>;
}
