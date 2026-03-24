export default function MainLayout({ children }: { children: React.ReactNode }) {
    return (
        <div className="layout-root">
            <header className="layout-header">
                <h1 className="logo">Meeting Mate</h1>
                <div className="header-actions">
                    {/* 필요하면 버튼 */}
                </div>
            </header>

            <main className="layout-main">
                {children}
            </main>
        </div>
    );
}