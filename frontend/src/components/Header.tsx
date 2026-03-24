import { Link, useNavigate } from "react-router-dom";
import Button from "./Button";

export default function Header() {
    const navigate = useNavigate();
    const isLoggedIn = !!localStorage.getItem("accessToken");

    const logout = () => {
        localStorage.removeItem("accessToken");
        navigate("/home");
    };

    return (
        <header className="w-full border-b">
            <div className="max-w-6xl mx-auto px-4 py-6 flex items-center justify-between">
                {/* Logo */}
                <h1 className="text-2xl font-bold">
                    <Link
                        to="/home"
                        className="text-inherit no-underline"
                    >
                        Meeting Mate
                    </Link>
                </h1>

                {/* Actions */}
                <div className="flex items-center gap-3">
                    {isLoggedIn ? (
                        <>
                            <Button
                                variant="secondary"
                                onClick={() => navigate("/mypage")}
                            >
                                My Page
                            </Button>

                            <Button
                                variant="secondary"
                                onClick={logout}
                            >
                                Logout
                            </Button>
                        </>
                    ) : (
                        <>
                            <Button
                                variant="secondary"
                                onClick={() => navigate("/login")}
                            >
                                Sign in
                            </Button>

                            <Button
                                variant="secondary"
                                onClick={() => navigate("/signup")}
                            >
                                Sign up
                            </Button>
                        </>
                    )}
                </div>
            </div>
        </header>
    );
}