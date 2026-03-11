export interface UserMe {
    id: number;
    email: string;
    nickname: string;
    provider: "LOCAL" | "GOOGLE" | "KAKAO";
    createdAt: string;
}
