import { motion } from "framer-motion";
import { useNavigate } from "react-router-dom";
import Header from "../components/Header";
import Button from "../components/Button";

export default function HomePage() {
    const navigate = useNavigate();

    return (
        <div className="min-h-screen flex flex-col bg-white">
            {/* 1. Sticky Header */}
            <div className="sticky top-0 z-50 bg-white border-b">
                <Header />
            </div>

            {/* Header 높이 보정 */}
            <div className="pt-20" />

            {/* 2. Hero */}
            <motion.section
                className="max-w-7xl mx-auto px-8 py-24 text-center"
                initial={{ opacity: 0, y: 40 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7 }}
            >
                <h2 className="text-4xl md:text-5xl font-bold mb-6">
                    장소 선정이 어려운 당신을 위해
                </h2>

                <p className="text-neutral-600 text-lg mb-10 leading-relaxed">
                    약속 장소와 분위기를 입력하면
                    <br />
                    AI가 추천해드립니다.
                </p>

                <Button variant="primary" onClick={() => navigate("/select")}>
                    시작하기
                </Button>
            </motion.section>

            {/* 3. 메인 그라데이션 */}
            <motion.section
                className="max-w-7xl mx-auto px-8"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.2, duration: 0.6 }}
            >
                <div className="w-full h-[260px] rounded-3xl bg-gradient-to-r from-pink-200 via-green-100 to-teal-300" />
            </motion.section>

            {/* 4. Feature 1 */}
            <motion.section
                className="max-w-7xl mx-auto px-8 py-32 grid md:grid-cols-2 gap-20 items-center"
                initial={{ opacity: 0, y: 40 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6 }}
            >
                <div>
                    <h3 className="text-3xl font-semibold mb-4">
                        AI 기반 장소 추천
                    </h3>
                    <p className="text-neutral-600 mb-8 leading-relaxed">
                        모임 목적과 분위기를 입력하면
                        <br />
                        AI가 어울리는 장소를 추천합니다.
                    </p>

                    <Button
                        variant="primary"
                        onClick={() => navigate("/select")}
                    >
                        추천 받아보기
                    </Button>
                </div>

                <div className="w-full h-[220px] rounded-3xl bg-gradient-to-r from-pink-200 to-pink-300" />
            </motion.section>

            {/* 5. Feature 2 */}
            <motion.section
                className="max-w-7xl mx-auto px-8 py-32 grid md:grid-cols-2 gap-20 items-center"
                initial={{ opacity: 0, y: 40 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.6 }}
            >
                <div className="w-full h-[220px] rounded-3xl bg-gradient-to-r from-purple-200 to-purple-100" />

                <div>
                    <h3 className="text-3xl font-semibold mb-4">
                        복잡한 선택은 AI에게
                    </h3>
                    <p className="text-neutral-600 mb-8 leading-relaxed">
                        고민할 필요 없이
                        <br />
                        결과만 확인하세요.
                    </p>

                    <Button
                        variant="primary"
                        onClick={() => navigate("/select")}
                    >
                        바로 시작
                    </Button>
                </div>
            </motion.section>

            {/* Footer */}
            <footer className="mt-auto py-10 border-t text-center text-sm text-neutral-500">
                © 2026 Meeting Mate
            </footer>
        </div>
    );
}