import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import {
    buildRedirectTarget,
    isAuthenticated,
    savePostLoginRedirect,
} from "../utils/auth";

export default function ProtectedRoute({ children }: { children: ReactNode }) {
    const location = useLocation();

    if (!isAuthenticated()) {
        const redirectTarget = buildRedirectTarget(
            location.pathname,
            location.search,
            location.hash
        );
        savePostLoginRedirect(redirectTarget);

        return (
            <Navigate
                to={`/login?redirect=${encodeURIComponent(redirectTarget)}`}
                replace
                state={{ message: "추천 기능은 로그인 후 사용할 수 있습니다." }}
            />
        );
    }

    return <>{children}</>;
}
