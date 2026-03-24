type ButtonProps = {
    children: React.ReactNode;
    onClick?: () => void;
    variant?: "primary" | "secondary";
    className?: string;
};

export default function Button({
                                   children,
                                   onClick,
                                   variant = "primary",
                                   className = "",
                               }: ButtonProps) {
    const base =
        "px-5 py-3 rounded-xl font-semibold transition-all";

    const variants = {
        primary: "bg-black text-white hover:bg-neutral-900",
        secondary:
            "bg-white border border-neutral-300 hover:bg-neutral-100",
    };

    return (
        <button
            onClick={onClick}
            className={`${base} ${variants[variant]} ${className}`}
        >
            {children}
        </button>
    );
}