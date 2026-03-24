import { BrowserRouter, Routes, Route } from "react-router-dom";
import OAuthCallbackPage from "./pages/OAuthCallbackPage";
import HomePage from "./pages/HomePage.tsx";
import SelectPage from "./pages/SelectPage.tsx";
import ResultPage from "./pages/ResultPage.tsx";
import SelectedRoutePage from "./pages/SelectedRoutePage.tsx";
import LoginPage from "./pages/LoginPage.tsx";
import MyPage from "./pages/MyPage.tsx";
import SignupPage from "./pages/SignupPage.tsx";
import ProtectedRoute from "./components/ProtectedRoute.tsx";

function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
                <Route path="/" element={<HomePage />} />
                <Route
                    path="/select"
                    element={
                        <ProtectedRoute>
                            <SelectPage />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/result"
                    element={
                        <ProtectedRoute>
                            <ResultPage />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/recommend"
                    element={
                        <ProtectedRoute>
                            <ResultPage />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/selected-route"
                    element={
                        <ProtectedRoute>
                            <SelectedRoutePage />
                        </ProtectedRoute>
                    }
                />
                <Route path={"/home"} element={<HomePage />} />
                <Route path={"/login"} element={<LoginPage />} />
                <Route path={"/signup"} element={<SignupPage />} />
                <Route
                    path={"/mypage"}
                    element={
                        <ProtectedRoute>
                            <MyPage />
                        </ProtectedRoute>
                    }
                />
            </Routes>
        </BrowserRouter>
    );
}

export default App;
